// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.*
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
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
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DeclarativeHintsEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(
    project: Project,
    file: VirtualFile,
    document: Document,
    editorSupplier: suspend () -> EditorEx,
    highlighterReady: suspend () -> Unit,
  ) {
    val grave = project.serviceAsync<DeclarativeHintsGrave>()
    val inlayDataList = grave.raise(file, document) ?: return
    val psiManager = project.serviceAsync<PsiManager>()
    val psiFile = readActionBlocking {
      psiManager.findFile(file)
    } ?: return
    val editor = editorSupplier()
    withContext(Dispatchers.EDT) {
      DeclarativeInlayHintsPass.applyInlayData(editor, psiFile, inlayDataList)
    }
  }
}

@Service(Level.PROJECT)
internal class DeclarativeHintsGrave(private val project: Project, private val scope: CoroutineScope)
  : TextEditorCache<DeclarativeHintsState>(project, scope),
    Disposable {

  override fun namePrefix(): String = "persistent-declarative-hints"
  override fun valueExternalizer(): DeclarativeHintsState.Externalizer = DeclarativeHintsState.Externalizer()
  override fun useHeapCache(): Boolean = true

  init {
    subscribeEditorClosed()
  }

  fun raise(file: VirtualFile, document: Document): List<InlayData>? {
    if (!isEnabled() || file !is VirtualFileWithId) {
      return null
    }
    val state: DeclarativeHintsState? = cache[file.id]
    if (state == null || state.contentHash != document.contentHash()) {
      return null
    }
    for (inlayData in state.inlayDataList) {
      initZombiePointers(file, inlayData.tree)
    }
    return state.inlayDataList
  }

  private fun bury(editor: Editor) {
    val file = editor.virtualFile
    if (!isEnabled() || editor.editorKind != EditorKind.MAIN_EDITOR || file !is VirtualFileWithId) {
      return
    }
    val declarativeHints = editor.getInlayModel().getInlineElementsInRange(
      0,
      editor.getDocument().textLength,
      DeclarativeInlayRenderer::class.java
    )
    if (declarativeHints.isEmpty()) {
      return
    }
    val contentHash = editor.document.contentHash()
    val inlayDataList = declarativeHints.map { inlay -> inlay.renderer.toInlayData() }.toList()
    scope.launch(Dispatchers.IO) {
      cache[file.id] = DeclarativeHintsState(contentHash, inlayDataList)
    }
  }

  override fun dispose() {
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

  private fun isEnabled() = Registry.`is`("cache.inlay.hints.on.disk")
}

class ZombieInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    throw UnsupportedOperationException("Zombie provider does not support inlay collecting")
  }
}
