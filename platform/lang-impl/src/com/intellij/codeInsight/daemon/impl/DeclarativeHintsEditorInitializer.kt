// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.*
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
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
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class DeclarativeHintsEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(
    project: Project,
    file: VirtualFile,
    document: Document,
    editorSupplier: suspend () -> EditorEx,
    highlighterReady: suspend () -> Unit,
  ) {
    if (isDeclarativeEnabled() && isCacheEnabled()) {
      val grave = project.serviceAsync<DeclarativeHintsGrave>()
      val settings = DeclarativeInlayHintsSettings.getInstance()
      val inlayDataList = grave.raise(file, document)
      if (inlayDataList.isNullOrEmpty()) {
        return
      }
      val inlayDataMap = readActionBlocking {
        inlayDataList.filter {
          settings.isProviderEnabled(it.providerId) ?: true
        }
      }.groupBy { it.sourceId }
      if (inlayDataMap.isEmpty()) {
        return
      }
      val editor = editorSupplier()
      withContext(Dispatchers.EDT) {
        inlayDataMap.forEach { (sourceId, inlayDataList) ->
          DeclarativeInlayHintsPass.applyInlayData(editor, project, inlayDataList, sourceId)
        }
        DeclarativeInlayHintsPassFactory.resetModificationStamp(editor)
      }
    }
  }
}

@Service(Level.PROJECT)
internal class DeclarativeHintsGrave(private val project: Project, private val scope: CoroutineScope)
  : TextEditorCache<DeclarativeHintsState>(project, scope), Disposable {
  override fun namePrefix(): String = "persistent-declarative-hints"
  override fun valueExternalizer(): DeclarativeHintsState.Externalizer = DeclarativeHintsState.Externalizer()
  override fun useHeapCache(): Boolean = true

  init {
    subscribeEditorClosed()
  }

  fun raise(file: VirtualFile, document: Document): List<InlayData>? {
    if (file !is VirtualFileWithId) {
      return null
    }
    val state = cache.get(file.id)
    if (state == null || state.contentHash != contentHash(document)) {
      return null
    }
    for (inlayData in state.inlayDataList) {
      initZombiePointers(file, inlayData.tree)
    }
    return state.inlayDataList
  }

  private fun bury(editor: Editor) {
    val file = editor.virtualFile
    if (editor.editorKind != EditorKind.MAIN_EDITOR || file !is VirtualFileWithId) {
      return
    }
    val declarativeHints = editor.getInlayModel().getInlineElementsInRange(
      0,
      editor.getDocument().textLength,
      DeclarativeInlayRenderer::class.java
    )
    val state = DeclarativeHintsState(contentHash(editor.document), inlayDataList(declarativeHints))
    scope.launch(Dispatchers.IO) {
      cache[file.id] = state
    }
  }

  override fun dispose() {
  }

  private fun subscribeEditorClosed() {
    EditorFactory.getInstance().addEditorFactoryListener(
      object : EditorFactoryListener {
        override fun editorReleased(event: EditorFactoryEvent) {
          if (isDeclarativeEnabled() && isCacheEnabled()) {
            bury(event.editor)
          }
        }
      },
      this
    )
  }

  private fun initZombiePointers(file: VirtualFile, tree: TinyTree<Any?>, index: Byte = 0) {
    val dataPayload = tree.getDataPayload(index)
    if (dataPayload is ActionWithContent) {
      val payload: InlayActionPayload = dataPayload.actionData.payload
      if (payload is PsiPointerInlayActionPayload) {
        val pointer = payload.pointer
        if (pointer is ZombieSmartPointer) {
          pointer.projectSupp = { project }
          pointer.fileSupp = { file }
        }
      }
    }
    tree.processChildren(index) { child ->
      initZombiePointers(file, tree, child)
      true
    }
  }

  private fun inlayDataList(declarativeHints: List<Inlay<out DeclarativeInlayRenderer>>): List<InlayData> {
    val inlayDataList = declarativeHints.map { inlay -> inlay.renderer.toInlayData() }
    if (isDebugEnabled()) {
      // transform inlayData -> byteArray -> inlayData to add '?' char at inlay presentation by PresentationTreeExternalizer
      val state = DeclarativeHintsState(0, inlayDataList).toByteArray()
      return DeclarativeHintsState.fromByteArray(state).inlayDataList
    }
    return inlayDataList
  }

  private fun isDebugEnabled() = Registry.`is`("cache.markup.debug", false)
}

private fun isCacheEnabled() = Registry.`is`("cache.inlay.hints.on.disk", true)

private fun isDeclarativeEnabled(): Boolean {
  val enabledGlobally = InlayHintsSettings.instance().hintsEnabledGlobally()
  return enabledGlobally && Registry.`is`("inlays.declarative.hints", true)
}

internal class ZombieInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    throw UnsupportedOperationException("Zombie provider does not support inlay collecting")
  }
}
