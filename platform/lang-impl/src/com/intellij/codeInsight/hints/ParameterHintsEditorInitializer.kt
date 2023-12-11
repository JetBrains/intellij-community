// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.ParameterHintsPass.HintData
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.fileEditor.impl.text.TextEditorInitializer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ParameterHintsEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(
    project: Project,
    file: VirtualFile,
    document: Document,
    editorSupplier: suspend () -> EditorEx,
    highlighterReady: suspend () -> Unit,
  ) {
    val hints = project.serviceAsync<ParamHintsGrave>().raise(file, document)
    if (hints == null) {
      return
    }
    val editor = editorSupplier()
    withContext(Dispatchers.EDT) {
      ParameterHintsUpdater(editor, listOf(), hints, Int2ObjectOpenHashMap(0), true).update()
    }
  }
}

@Service(Level.PROJECT)
internal class ParamHintsGrave(project: Project, private val scope: CoroutineScope)
  : TextEditorCache<ParameterHintsState>(project, scope),
    Disposable
{
  override fun namePrefix() = "persistent-parameter-hints"
  override fun valueExternalizer() = ParameterHintsState.Externalizer
  override fun useHeapCache() = true

  init {
    subscribeEditorClosed()
  }

  private fun subscribeEditorClosed() {
    EditorFactory.getInstance().addEditorFactoryListener(
      object : EditorFactoryListener {
        override fun editorReleased(event: EditorFactoryEvent) {
          bury(event.editor)
        }
      },
      this
    )
  }

  private fun bury(editor: Editor) {
    val file = editor.virtualFile
    if (!isEnabled() || editor.editorKind != EditorKind.MAIN_EDITOR || file !is VirtualFileWithId) {
      return
    }
    val inlays = ParameterHintsPresentationManager.getInstance().getParameterHintsInRange(editor, 0, editor.document.textLength)
    val hints = inlays.mapNotNull { inlay -> inlay.toHintData() }.toList()
    if (hints.isEmpty()) {
      return
    }
    val contentHash = editor.document.contentHash()
    scope.launch(Dispatchers.IO) {
      cache[file.id] = ParameterHintsState(contentHash, hints)
    }
  }

  private fun Inlay<*>.toHintData(): Pair<Int, HintData>? {
    val renderer = this.renderer as? HintRenderer
    val text = renderer?.text
    return if (renderer != null && text != null) {
      Pair(offset, HintData(text, isRelatedToPrecedingText, renderer.widthAdjustment))
    } else {
      null
    }
  }

  override fun dispose() {
  }

  fun raise(file: VirtualFile, document: Document): Int2ObjectOpenHashMap<MutableList<HintData>>? {
    if (!isEnabled() || file !is VirtualFileWithId) {
      return null
    }
    val state: ParameterHintsState? = cache[file.id]
    if (state == null || state.contentHash != document.contentHash()) {
      return null
    }
    val hintMap = Int2ObjectOpenHashMap<MutableList<HintData>>()
    for ((offset, hint) in state.hints) {
      val list: MutableList<HintData> = hintMap.getOrPut(offset) { ArrayList() }
      if (isDebug()) {
        list.add(debugHintData(hint))
      } else {
        list.add(hint)
      }
    }
    return hintMap
  }

  private fun debugHintData(hintData: HintData): HintData {
    val text = hintData.presentationText
    val colonIndex = text.lastIndexOf(':')
    val debugText = if (colonIndex == -1) {
      "$text?"
    } else {
      text.substring(0, colonIndex) + "?:"
    }
    return HintData(debugText, hintData.relatesToPrecedingText, hintData.widthAdjustment)
  }

  private fun isEnabled() = Registry.`is`("cache.inlay.hints.on.disk")

  private fun isDebug() = Registry.`is`("cache.markup.debug")
}
