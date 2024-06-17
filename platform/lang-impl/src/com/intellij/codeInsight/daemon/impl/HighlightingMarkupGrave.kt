// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.MarkupGraveEvent
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.logEditorMarkupGrave
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesModelImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.markupRestored
import com.intellij.util.containers.ConcurrentIntObjectMap
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import com.intellij.util.messages.SimpleMessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.DataInput
import java.io.DataOutput
import java.util.*
import javax.swing.Icon

private val LOG = logger<HighlightingMarkupGrave>()
private val IS_ZOMBIE = Key.create<Boolean>("IS_ZOMBIE")

private fun markZombieMarkup(highlighter: RangeMarker) {
  highlighter.putUserData(IS_ZOMBIE, true)
}

/**
 * Stores the highlighting markup on disk on file close and restores it back to the editor on file open,
 * to reduce the "opened editor-to-some highlighting shown" perceived interval.
 */
@ApiStatus.Internal
open class HighlightingMarkupGrave(project: Project, private val coroutineScope: CoroutineScope) {
  protected val project: Project
  private val resurrectedZombies: ConcurrentIntObjectMap<Boolean> // fileId -> isMarkupModelPreferable
  private val markupStore: HighlightingMarkupStore

  companion object {
    val isEnabled: Boolean
      get() = Registry.`is`("cache.highlighting.markup.on.disk", true)

    @TestOnly
    fun runInEnabled(runnable: Runnable) {
      val wasEnabled = isEnabled
      Registry.get("cache.highlighting.markup.on.disk").setValue(true)
      try {
        runnable.run()
      }
      finally {
        Registry.get("cache.highlighting.markup.on.disk").setValue(wasEnabled)
      }
    }

    fun isZombieMarkup(highlighter: RangeMarker): Boolean = highlighter.getUserData(IS_ZOMBIE) != null

    fun unmarkZombieMarkup(highlighter: RangeMarker) {
      highlighter.putUserData(IS_ZOMBIE, null)
    }
  }

  init {
    // check that important TextAttributesKeys are initialized
    checkNotNull(DefaultLanguageHighlighterColors.INSTANCE_FIELD.fallbackAttributeKey) { DefaultLanguageHighlighterColors.INSTANCE_FIELD }

    this.project = project
    resurrectedZombies = ConcurrentCollectionFactory.createConcurrentIntObjectMap()
    markupStore = HighlightingMarkupStore(project, coroutineScope)

    val connection = project.messageBus.connect(coroutineScope)
    @Suppress("LeakingThis")
    subscribeDaemonFinished(connection)
    subscribeFileClosed(connection)
  }

  protected open fun subscribeDaemonFinished(connection: SimpleMessageBusConnection) {
    // as soon as highlighting kicks in and displays its own range highlighters, remove ones we applied from the on-disk cache,
    // but only after the highlighting finished, to avoid flicker
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
      override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        if (!DumbService.getInstance(project).isDumb) {
          for (fileEditor in fileEditors) {
            if (fileEditor is TextEditor && shouldPutDownActiveZombiesInFile(fileEditor)) {
              val file = fileEditor.file
              if (file is VirtualFileWithId) {
                putDownActiveZombiesInFile(file, fileEditor.editor)
              }
            }
          }
        }
      }
    })
  }

  protected open fun shouldPutDownActiveZombiesInFile(textEditor: TextEditor): Boolean {
    return textEditor.editor.editorKind == EditorKind.MAIN_EDITOR && DaemonCodeAnalyzerEx.isHighlightingCompleted(textEditor, project)
  }

  private fun subscribeFileClosed(connection: SimpleMessageBusConnection) {
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, object : FileEditorManagerListener.Before {
      override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) {
        putInGrave(source, file)
      }
    })
  }

  protected fun putDownActiveZombiesInFile(fileWithId: VirtualFileWithId, editor: Editor) {
    val replaced = resurrectedZombies.replace(fileWithId.id, false, true)
    if (!replaced) {
      // no zombie or zombie already disposed
      return
    }

    var toRemove: MutableList<RangeHighlighter>? = null
    val markupModel = DocumentMarkupModel.forDocument(editor.document, project, false)
    if (markupModel != null) {
      for (highlighter in markupModel.allHighlighters) {
        if (isZombieMarkup(highlighter)) {
          if (toRemove == null) {
            toRemove = ArrayList()
          }
          toRemove.add(highlighter)
        }
      }
    }
    if (toRemove == null) {
      return
    }

    LOG.debug { "removing ${toRemove.size} markups for $editor; dumb=${DumbService.getInstance(project).isDumb}" }

    for (highlighter in toRemove) {
      highlighter.dispose()
    }
  }

  internal fun resurrectZombies(document: Document, file: VirtualFileWithId) {
    if (resurrectedZombies.containsKey(file.id)) {
      return
    }

    val markupInfo = markupStore.getMarkup(file)
    if (markupInfo == null) {
      resurrectedZombies.put(file.id, true)
      logFusStatistic(file, MarkupGraveEvent.NOT_RESTORED_CACHE_MISS)
      return
    }

    if (TextEditorCache.contentHash(document) != markupInfo.contentHash) {
      // text changed since the cached markup was saved on-disk
      LOG.debug { "restore canceled hash mismatch ${markupInfo.size()} for $file" }
      markupStore.removeMarkup(file)
      resurrectedZombies.put(file.id, true)
      logFusStatistic(file, MarkupGraveEvent.NOT_RESTORED_CONTENT_CHANGED)
      return
    }

    val markupModel = DocumentMarkupModel.forDocument(document, project, true)
    for (state in markupInfo.highlighters) {
      val textLength = document.textLength
      if (state.end > textLength) {
        // something's wrong, the document has changed in the other thread?
        LOG.warn("skipped $state as it is out of document with length $textLength")
        continue
      }

      var highlighter: RangeHighlighter
      // re-read TextAttributesKey because it might be read too soon, with its myFallbackKey uninitialized.
      // (still store TextAttributesKey by instance, instead of String, to intern its external name)
      val key = if (state.textAttributesKey == null) null else TextAttributesKey.find(state.textAttributesKey.externalName)
      if (key == null) {
        highlighter = markupModel.addRangeHighlighter(state.start, state.end, state.layer, state.textAttributes, state.targetArea)
      }
      else {
        highlighter = markupModel.addRangeHighlighter(key, state.start, state.end, state.layer, state.targetArea)
        if (StickyLinesModelImpl.isStickyLine(highlighter)) {
          StickyLinesModelImpl.skipInAllEditors(highlighter)
        }
      }

      if (state.gutterIcon != null) {
        val fakeIcon: GutterIconRenderer = object : GutterIconRenderer() {
          override fun equals(other: Any?): Boolean = false

          override fun hashCode(): Int = 0

          override fun getIcon(): Icon = state.gutterIcon
        }
        highlighter.gutterIconRenderer = fakeIcon
      }
      markZombieMarkup(highlighter)
    }

    logFusStatistic(file, MarkupGraveEvent.RESTORED, markupInfo.size())
    markupRestored(file)
    LOG.debug { "restored ${markupInfo.size()} for $file" }
    resurrectedZombies.put(file.id, false)
  }

  private fun putInGrave(editorManager: FileEditorManager, file: VirtualFile) {
    if (file !is VirtualFileWithId) {
      return
    }

    val fileEditor = editorManager.getSelectedEditor(file)
    if (fileEditor !is TextEditor) {
      return
    }

    if (fileEditor.editor.editorKind != EditorKind.MAIN_EDITOR) {
      return
    }

    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return
    val colorsScheme = fileEditor.editor.colorsScheme
    coroutineScope.launch {
      readActionBlocking {
        val markupFromModel = getMarkupFromModel(document, colorsScheme)
        val storedMarkup = markupStore.getMarkup(file)
        val zombieDisposed = resurrectedZombies[file.id]
        val graveDecision = getCacheDecision(newMarkup = markupFromModel, oldMarkup = storedMarkup, isNewMoreRelevant = zombieDisposed)
        when (graveDecision) {
          CacheDecision.STORE_NEW -> {
            markupStore.putMarkup(file, markupFromModel)
            LOG.debug { "stored markup ${markupFromModel.size()} for $file" }
          }
          CacheDecision.REMOVE_OLD -> {
            markupStore.removeMarkup(file)
            if (storedMarkup != null && LOG.isDebugEnabled) {
              LOG.debug("removed outdated markup ${storedMarkup.size()} for $file")
            }
          }
          CacheDecision.KEEP_OLD -> {
            if (storedMarkup != null && LOG.isDebugEnabled) {
              LOG.debug("preserved markup ${storedMarkup.size()} for $file")
            }
          }
        }
      }
    }
  }

  private fun allHighlightersFromMarkup(
    project: Project,
    document: Document,
    colorsScheme: EditorColorsScheme
  ): List<HighlighterState> {
    val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return emptyList()
    return markupModel.allHighlighters
      .asSequence()
      .filter { shouldSaveHighlighter(it) }
      .map { HighlighterState(it, getHighlighterLayer(it), colorsScheme) }
      .toList()
  }

  protected open fun shouldSaveHighlighter(highlighter: RangeHighlighter): Boolean {
    if (StickyLinesModelImpl.isStickyLine(highlighter)) {
      return true
    }

    val info = HighlightInfo.fromRangeHighlighter(highlighter)
    if (info != null &&
        (info.severity > HighlightSeverity.INFORMATION // either warning/error or symbol type (e.g., field text attribute)
         || info.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY)
    ) {
      return true
    }

    val lm = LineMarkersUtil.getLineMarkerInfo(highlighter)
    return lm != null && lm.icon != null // or a line marker with a gutter icon
  }

  protected open fun getHighlighterLayer(highlighter: RangeHighlighter): Int = highlighter.layer

  private fun getMarkupFromModel(document: Document, colorsScheme: EditorColorsScheme): FileMarkupInfo {
    return FileMarkupInfo(
      contentHash = TextEditorCache.contentHash(document),
      highlighters = allHighlightersFromMarkup(project = project, document = document, colorsScheme = colorsScheme)
    )
  }

  @TestOnly
  fun clearResurrectedZombies() {
    resurrectedZombies.clear()
  }

  private fun logFusStatistic(file: VirtualFileWithId, event: MarkupGraveEvent, restoredCount: Int = 0) {
    logEditorMarkupGrave(project = project, file = file as VirtualFile, graveEvent = event, restoredCount = restoredCount)
  }
}

internal data class FileMarkupInfo(@JvmField val contentHash: Int, @JvmField val highlighters: List<HighlighterState>) {
  fun bury(out: DataOutput) {
    DataInputOutputUtil.writeINT(out, contentHash)
    DataInputOutputUtil.writeINT(out, highlighters.size)
    for (highlighterState in highlighters) {
      highlighterState.bury(out)
    }
  }

  val isEmpty: Boolean
    get() = highlighters.isEmpty()

  fun size(): Int = highlighters.size

  companion object {
    fun readFileMarkupInfo(`in`: DataInput): FileMarkupInfo {
      val contentHash = DataInputOutputUtil.readINT(`in`)
      val hCount = DataInputOutputUtil.readINT(`in`)
      val highlighters = ArrayList<HighlighterState>(hCount)
      for (i in 0 until hCount) {
        val highlighterState = HighlighterState.readHighlighterState(`in`)
        highlighters.add(highlighterState)
      }
      return FileMarkupInfo(contentHash, Collections.unmodifiableList(highlighters))
    }
  }
}

internal data class HighlighterState(
  @JvmField val start: Int,
  @JvmField val end: Int,
  @JvmField val layer: Int,
  @JvmField val targetArea: HighlighterTargetArea,
  @JvmField val textAttributesKey: TextAttributesKey?,
  @JvmField val textAttributes: TextAttributes?,
  @JvmField val gutterIcon: Icon?
) {
  constructor(highlighter: RangeHighlighter, highlighterLayer: Int, colorsScheme: EditorColorsScheme) : this(
    start = highlighter.startOffset,
    end = highlighter.endOffset,
    layer = highlighterLayer,  //because Rider needs to modify its zombie's layers
    targetArea = highlighter.targetArea,
    textAttributesKey = highlighter.textAttributesKey,
    textAttributes = highlighter.getTextAttributes(colorsScheme),
    gutterIcon = if (highlighter.gutterIconRenderer == null) null else highlighter.gutterIconRenderer!!.icon
  )

  fun bury(out: DataOutput) {
    DataInputOutputUtil.writeINT(out, start)
    DataInputOutputUtil.writeINT(out, end)
    DataInputOutputUtil.writeINT(out, layer)
    DataInputOutputUtil.writeINT(out, targetArea.ordinal)
    writeTextAttributesKey(out)
    writeTextAttributes(out)
    writeGutterIcon(gutterIcon, out)
  }

  private fun writeTextAttributesKey(out: DataOutput) {
    val attributeKeyExists = textAttributesKey != null
    out.writeBoolean(attributeKeyExists)
    if (attributeKeyExists) {
      IOUtil.writeUTF(out, textAttributesKey!!.externalName)
    }
  }

  private fun writeTextAttributes(out: DataOutput) {
    val attributesExists = textAttributes != null && textAttributesKey == null
    out.writeBoolean(attributesExists)
    if (attributesExists) {
      textAttributes!!.writeExternal(out)
    }
  }

  override fun equals(other: Any?): Boolean {
    // exclude gutterIcon
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val state = other as HighlighterState
    return start == state.start && end == state.end && layer == state.layer && targetArea == state.targetArea &&
           textAttributesKey == state.textAttributesKey &&
           textAttributes == state.textAttributes
  }

  override fun hashCode(): Int {
    // exclude gutterIcon
    return Objects.hash(start, end, layer, targetArea, textAttributesKey, textAttributes)
  }

  companion object {
    fun readHighlighterState(`in`: DataInput): HighlighterState {
      val start = DataInputOutputUtil.readINT(`in`)
      val end = DataInputOutputUtil.readINT(`in`)
      val layer = DataInputOutputUtil.readINT(`in`)
      val target = DataInputOutputUtil.readINT(`in`)
      val key = if (`in`.readBoolean()) TextAttributesKey.find(IOUtil.readUTF(`in`)) else null
      val attributes = if (`in`.readBoolean()) TextAttributes(`in`) else null
      val icon = readGutterIcon(`in`)
      return HighlighterState(start, end, layer, HighlighterTargetArea.entries[target], key, attributes, icon)
    }
  }
}

private enum class CacheDecision {
  STORE_NEW,
  KEEP_OLD,
  REMOVE_OLD;
}

private fun getCacheDecision(
  newMarkup: FileMarkupInfo,
  oldMarkup: FileMarkupInfo?,
  isNewMoreRelevant: Boolean?
): CacheDecision {
  return when {
    // put zombie's limbs
    oldMarkup == null && !newMarkup.isEmpty -> CacheDecision.STORE_NEW
    // no, a limb to put in grave
    oldMarkup == null -> CacheDecision.KEEP_OLD
    // fresh limbs
    oldMarkup.contentHash != newMarkup.contentHash && !newMarkup.isEmpty -> CacheDecision.STORE_NEW
    // graved zombie is rotten and there is no a limb to bury
    oldMarkup.contentHash != newMarkup.contentHash -> CacheDecision.REMOVE_OLD
    // graved zombie is still fresh
    newMarkup.isEmpty -> CacheDecision.KEEP_OLD
    // should never happen. the file is closed without being opened before
    isNewMoreRelevant == null -> CacheDecision.STORE_NEW
    // limbs form complete zombie
    isNewMoreRelevant -> CacheDecision.STORE_NEW
    else -> CacheDecision.KEEP_OLD
  }
}