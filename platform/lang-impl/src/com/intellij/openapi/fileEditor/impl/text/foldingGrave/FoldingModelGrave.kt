// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.FoldingModelImpl.ZOMBIE_REGION_KEY
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Level.PROJECT)
internal class FoldingModelGrave(
  project: Project,
  private val scope: CoroutineScope
) : TextEditorCache<FoldingState>(project, scope), Disposable {

  override fun namePrefix(): String = "persistent-folding"
  override fun valueExternalizer(): FoldingState.FoldingStateExternalizer = FoldingState.FoldingStateExternalizer
  override fun useHeapCache(): Boolean = true

  companion object {
    private val logger = Logger.getInstance(FoldingModelGrave::class.java)
  }

  fun raise(file: VirtualFile?): FoldingState? {
    if (isEnabled() && file is VirtualFileWithId) {
      return cache[file.id]
    }
    return null
  }

  fun subscribeEditorClosed() {
    EditorFactory.getInstance().addEditorFactoryListener(
      object : EditorFactoryListener {
        override fun editorReleased(event: EditorFactoryEvent) {
          if (isEnabled()) {
            bury(event.editor)
          }
        }
      },
      this
    )
  }

  private fun bury(editor: Editor) {
    val file = editor.virtualFile
    if (file is VirtualFileWithId && editor.editorKind == EditorKind.MAIN_EDITOR) {
      val foldRegions = notZombieRegions(editor)
      if (foldRegions.isNotEmpty()) {
        val foldingState = FoldingState.create(editor.document.contentHash(), foldRegions)
        scope.launch(Dispatchers.IO) {
          cache[file.id] = foldingState
          logger.debug { "stored folding state ${foldingState} for $file" }
        }
      }
    }
  }

  private fun notZombieRegions(editor: Editor): List<FoldRegion> {
    return editor.foldingModel.allFoldRegions.filter { it.getUserData(ZOMBIE_REGION_KEY) == null }
  }

  override fun dispose() {
  }

  private fun isEnabled(): Boolean {
    return Registry.`is`("cache.folding.model.on.disk")
  }
}
