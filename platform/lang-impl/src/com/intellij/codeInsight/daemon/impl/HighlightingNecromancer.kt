// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.MarkupGraveEvent
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.logEditorMarkupGrave
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesModelImpl
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import com.intellij.util.CommonProcessors
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ConcurrentIntObjectMap
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon

@Internal
open class HighlightingNecromancerAwaker : NecromancerAwaker<HighlightingZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<HighlightingZombie> {
    val necromancer = HighlightingNecromancer(project, coroutineScope)
    necromancer.subscribeDaemonFinished()
    return necromancer
  }
}

@Internal
open class HighlightingNecromancer(
  protected val project: Project,
  protected val coroutineScope: CoroutineScope,
) : GravingNecromancer<HighlightingZombie>(
  project,
  coroutineScope,
  GRAVED_HIGHLIGHTING,
  HighlightingNecromancy,
) {
  private val zombieStatusMap: ConcurrentIntObjectMap<Status> = ConcurrentCollectionFactory.createConcurrentIntObjectMap()

  private enum class Status {
    SPAWNED,
    DISPOSED,
    NO_ZOMBIE,
  }

  override fun turnIntoZombie(recipe: TurningRecipe): HighlightingZombie? {
    if (isEnabled()) {
      val markupModel = DocumentMarkupModel.forDocument(recipe.document, recipe.project, false)
      if (markupModel is MarkupModelEx) {
        val colorsScheme = recipe.editor.colorsScheme
        val collector = HighlighterCollector()
        markupModel.processRangeHighlightersOverlappingWith(0, recipe.document.textLength, collector)
        val highlighters = collector.results.map { highlighter ->
          HighlightingLimb(highlighter, getHighlighterLayer(highlighter), colorsScheme)
        }.toList()
        return HighlightingZombie(highlighters)
      }
    }
    return null
  }

  override suspend fun shouldBuryZombie(recipe: TurningRecipe, zombie: HighlightingZombie): Boolean {
    val zombieStatus = zombieStatusMap[recipe.fileId]
    val graveDecision = getGraveDecision(
      newZombie = zombie,
      oldZombie = zombieStatus,
      file = recipe.file,
    )
    return when (graveDecision) {
      GraveDecision.BURY_NEW -> {
        LOG.debug { "put in grave zombie with ${zombie.limbs().size} libs for ${fileName(recipe.file)}" }
        true
      }
      GraveDecision.REMOVE_OLD -> {
        LOG.debug { "remove old zombie for ${fileName(recipe.file)}" }
        buryZombie(recipe.fileId, null)
        false
      }
      GraveDecision.KEEP_OLD -> {
        LOG.debug { "keep old zombie for ${fileName(recipe.file)}" }
        false
      }
    }
  }

  override suspend fun shouldSpawnZombie(recipe: SpawnRecipe): Boolean {
    return isEnabled() && !zombieStatusMap.containsKey(recipe.fileId)
  }

  override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: HighlightingZombie?) {
    if (zombie == null || zombie.limbs().isEmpty()) {
      zombieStatusMap.put(recipe.fileId, Status.NO_ZOMBIE)
      logFusStatistic(recipe.file, MarkupGraveEvent.NOT_RESTORED_CACHE_MISS)
      LOG.debug { "no zombie to spawn for ${fileName(recipe.file)}" }
    } else {
      val markupModel = DocumentMarkupModel.forDocument(recipe.document, project, true)

      // we have to make sure that editor highlighter is created before we start raising zombies
      // because creation of highlighter has a side effect that TextAttributesKey.ourRegistry is filled with corresponding keys
      // (e.g. class loading of org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors)
      // without such guarantee there is a risk to get uninitialized fallbackKey in TextAttributesKey.find(externalName)
      // it may lead to incorrect color of highlighters on startup
      recipe.highlighterReady()

      val spawned = spawnZombie(markupModel, recipe, zombie)
      zombieStatusMap.put(recipe.fileId, if (spawned == 0) Status.NO_ZOMBIE else Status.SPAWNED)
      logFusStatistic(recipe.file, MarkupGraveEvent.RESTORED, spawned)
      if (spawned != 0) {
        FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.HIGHLIGHTING)
      }
      LOG.debug { "spawned zombie with ${spawned}/${zombie.limbs().size} libs for ${fileName(recipe.file)}" }
    }
  }

  open fun subscribeDaemonFinished() {
    // as soon as highlighting kicks in and displays its own range highlighters, remove ones we applied from the on-disk cache,
    // but only after the highlighting finished, to avoid flicker
    project.messageBus.connect(coroutineScope).subscribe(
      DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
      object : DaemonCodeAnalyzer.DaemonListener {
        override fun daemonFinished(fileEditors: Collection<FileEditor>) {
          if (!DumbService.getInstance(project).isDumb) {
            for (fileEditor in fileEditors) {
              if (fileEditor is TextEditor && shouldPutDownActiveZombiesInFile(fileEditor)) {
                val document = fileEditor.editor.document
                val file = FileDocumentManager.getInstance().getFile(document)
                if (file is VirtualFileWithId) {
                  putDownActiveZombiesInFile(file, document)
                }
              }
            }
          }
        }
      },
    )
  }

  protected open fun shouldPutDownActiveZombiesInFile(textEditor: TextEditor): Boolean {
    return textEditor.editor.editorKind == EditorKind.MAIN_EDITOR &&
           DaemonCodeAnalyzerEx.isHighlightingCompleted(textEditor, project)
  }

  protected open fun shouldBuryHighlighter(highlighter: RangeHighlighterEx): Boolean {
    if (StickyLinesModelImpl.isStickyLine(highlighter)) {
      // hack for sticky lines since they are implemented via markupModel
      return true
    }
    if (highlighter.editorFilter != MarkupEditorFilter.EMPTY) {
      // skip highlighters with non-default filter to avoid appearing filtered highlighters
      return false
    }
    val severity = HighlightInfo.fromRangeHighlighter(highlighter)?.severity
    if (severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY ||
        (severity != null && severity >= HighlightSeverity.INFORMATION)) {
      return true
    }
    val lineMarker = LineMarkersUtil.getLineMarkerInfo(highlighter)
    // or a line marker with a gutter icon
    return lineMarker?.icon != null
  }

  protected open fun getHighlighterLayer(highlighter: RangeHighlighterEx): Int {
    return highlighter.layer
  }

  protected fun putDownActiveZombiesInFile(file: VirtualFileWithId, document: Document) {
    val replaced = zombieStatusMap.replace(file.id, Status.SPAWNED, Status.DISPOSED)
    if (!replaced) {
      // no zombie or zombie already disposed
      return
    }
    val markupModel = DocumentMarkupModel.forDocument(document, project, false)
    if (markupModel != null) {
      val zombies = markupModel.allHighlighters.filter { isZombieMarkup(it) }.toList()
      for (highlighter in zombies) {
        highlighter.dispose()
      }
      LOG.debug { "disposed zombies ${zombies.size} for file ${fileName(file)}" }
    }
  }

  private suspend fun spawnZombie(markupModel: MarkupModel, recipe: SpawnRecipe, zombie: HighlightingZombie): Int {
    var spawned = 0
    if (recipe.isValid()) {
      // restore highlighters with batches to balance between RA duration and RA count
      val batchSize = RA_BATCH_SIZE
      val batchCount = zombie.limbs().size / batchSize
      for (batchNum in 0 until batchCount) {
        val abortSpawning = readActionBlocking {
          val isValid = recipe.isValid() // ensure document not changed
          if (isValid) {
            for (limbNumInBatch in 0 until batchSize) {
              val limbNum = batchNum * batchSize + limbNumInBatch
              createRangeHighlighter(markupModel, zombie.limbs()[limbNum])
              spawned++
            }
          }
          !isValid
        }
        if (abortSpawning) {
          return spawned
        }
      }
      readActionBlocking {
        if (recipe.isValid()) {
          for (limbNum in (batchCount * batchSize) until zombie.limbs().size) {
            createRangeHighlighter(markupModel, zombie.limbs()[limbNum])
            spawned++
          }
        }
      }
      assert(spawned == zombie.limbs().size) {
        "expected: ${zombie.limbs().size}, actual: $spawned"
      }
    }
    return spawned
  }

  private fun createRangeHighlighter(markupModel: MarkupModel, limb: HighlightingLimb) {
    ThreadingAssertions.assertReadAccess()

    val highlighter = if (limb.textAttributesKey != null) {
      // re-read TextAttributesKey because it might be read too soon, with its myFallbackKey uninitialized.
      // (still store TextAttributesKey by instance, instead of String, to intern its external name)
      // see recipe.highlighterReady()
      val key = TextAttributesKey.find(limb.textAttributesKey.externalName)
      markupModel.addRangeHighlighter(
        key,
        limb.startOffset,
        limb.endOffset,
        limb.layer,
        limb.targetArea,
      )
    } else {
      markupModel.addRangeHighlighter(
        limb.startOffset,
        limb.endOffset,
        limb.layer,
        limb.textAttributes, // TODO: why attributes is null sometimes?
        limb.targetArea,
      )
    }
    highlighter.putUserData(IS_ZOMBIE, true)
    if (limb.gutterIcon != null) {
      highlighter.gutterIconRenderer = ZombieIcon(limb.gutterIcon)
    }
    if (StickyLinesModelImpl.isStickyLine(highlighter)) {
      StickyLinesModelImpl.skipInAllEditors(highlighter)
    }
  }

  private fun logFusStatistic(file: VirtualFile, event: MarkupGraveEvent, restoredCount: Int = 0) {
    logEditorMarkupGrave(project, file, event, restoredCount)
  }

  @TestOnly
  private fun clearSpawnedZombies() {
    zombieStatusMap.clear()
  }

  private fun fileName(file: VirtualFileWithId): String {
    return fileName(file as VirtualFile)
  }

  private fun fileName(file: VirtualFile): String {
    return "file(id=${(file as VirtualFileWithId).id}, name=${file.name})"
  }

  private inner class HighlighterCollector : CommonProcessors.CollectProcessor<RangeHighlighterEx>() {
    override fun accept(highlighter: RangeHighlighterEx?): Boolean {
      return highlighter != null && shouldBuryHighlighter(highlighter)
    }
  }

  private enum class GraveDecision {
    BURY_NEW,
    KEEP_OLD,
    REMOVE_OLD,
  }

  private fun getGraveDecision(
    newZombie: HighlightingZombie,
    oldZombie: Status?,
    file: VirtualFile,
  ): GraveDecision {
    val decision = when (oldZombie) {
      null -> GraveDecision.KEEP_OLD
      Status.SPAWNED -> GraveDecision.KEEP_OLD
      Status.DISPOSED -> GraveDecision.BURY_NEW
      Status.NO_ZOMBIE -> {
        if (newZombie.limbs().isNotEmpty()) {
          GraveDecision.BURY_NEW
        } else {
          GraveDecision.REMOVE_OLD
        }
      }
    }
    LOG.debug {
      "grave decision $decision based on old zombie $oldZombie and " +
      "new zombie with ${newZombie.limbs().size} limbs for ${fileName(file)}"
    }
    return decision
  }

  private class ZombieIcon(private val icon: Icon) : GutterIconRenderer() {
    override fun equals(other: Any?): Boolean = false
    override fun hashCode(): Int = 0
    override fun getIcon(): Icon = icon
  }

  companion object {
    private const val GRAVED_HIGHLIGHTING = "graved-highlighting"

    private const val RA_BATCH_SIZE = 3_000

    private val LOG = logger<HighlightingNecromancer>()

    private val IS_ZOMBIE = Key.create<Boolean>("IS_ZOMBIE")

    private fun isEnabled(): Boolean {
      return Registry.`is`("cache.highlighting.markup.on.disk", true)
    }

    @JvmStatic
    fun isZombieMarkup(highlighter: RangeMarker): Boolean {
      return highlighter.getUserData(IS_ZOMBIE) != null
    }

    @JvmStatic
    fun unmarkZombieMarkup(highlighter: RangeMarker) {
      highlighter.putUserData(IS_ZOMBIE, null)
    }

    @JvmStatic
    @TestOnly
    fun runInEnabled(runnable: Runnable) {
      val wasEnabled = isEnabled()
      Registry.get("cache.highlighting.markup.on.disk").setValue(true)
      try {
        runnable.run()
      } finally {
        Registry.get("cache.highlighting.markup.on.disk").setValue(wasEnabled)
      }
    }

    @JvmStatic
    @TestOnly
    fun clearSpawnedZombies(project: Project) {
      val service = project.service<Necropolis>()
      (service.necromancerByName(GRAVED_HIGHLIGHTING) as HighlightingNecromancer).clearSpawnedZombies()
    }
  }
}
