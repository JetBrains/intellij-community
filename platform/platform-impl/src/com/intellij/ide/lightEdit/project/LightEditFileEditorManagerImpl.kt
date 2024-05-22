// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project

import com.intellij.ide.lightEdit.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorComposite.Companion.EMPTY
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import org.jdom.Element

class LightEditFileEditorManagerImpl internal constructor(project: Project,
                                                          coroutineScope: CoroutineScope) : LightEditFileEditorManagerBase(project,
                                                                                                                           coroutineScope) {
  override fun loadState(state: Element) {
    // do not open previously opened files
  }

  override fun getState(): Element? {
    return null
  }

  override fun openFileImpl2(window: EditorWindow,
                             file: VirtualFile,
                             options: FileEditorOpenOptions): FileEditorComposite {
    LightEditService.getInstance().openFile(file)
    val data = getSelectedEditorWithProvider(file)
    return if (data == null) EMPTY
    else object : FileEditorComposite {
      override val allEditors: List<FileEditor>
        get() = java.util.List.of(data.fileEditor)

      override val allProviders: List<FileEditorProvider>
        get() = java.util.List.of(data.provider)

      override val isPreview: Boolean
        get() = false
    }
  }

  override fun getEditorsWithProviders(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    val data = getSelectedEditorWithProvider(file)
    return if (data == null
    ) Pair(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY)
    else Pair(arrayOf(data.fileEditor), arrayOf(data.provider))
  }

  override fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider? {
    val editorManager = LightEditService.getInstance().editorManager as LightEditorManagerImpl
    val editorInfo = editorManager.findOpen(file) as LightEditorInfoImpl?
    if (editorInfo != null) {
      return FileEditorWithProvider(editorInfo.fileEditor, editorInfo.provider)
    }
    return null
  }

  override fun getSelectedEditor(): FileEditor? {
    return LightEditService.getInstance().selectedFileEditor
  }

  override fun getSelectedTextEditor(): Editor? {
    return LightEditorInfoImpl.getEditor(selectedEditor)
  }

  override fun getOpenFiles(): Array<VirtualFile> {
    return VfsUtilCore.toVirtualFileArray(LightEditService.getInstance().editorManager.openFiles)
  }

  override fun isFileOpen(file: VirtualFile): Boolean {
    return LightEditService.getInstance().editorManager.isFileOpen(file)
  }

  override fun hasOpenedFile(): Boolean {
    return !LightEditService.getInstance().editorManager.openFiles.isEmpty()
  }

  override fun getSelectedFiles(): Array<VirtualFile> {
    val file = LightEditService.getInstance().selectedFile
    return if (file != null) arrayOf(file) else VirtualFile.EMPTY_ARRAY
  }

  override val currentFile: VirtualFile?
    get() = LightEditService.getInstance().selectedFile


  override fun hasOpenFiles(): Boolean {
    return !LightEditService.getInstance().editorManager.openFiles.isEmpty()
  }

  fun createEditorComposite(editorInfo: LightEditorInfo): EditorComposite {
    editorInfo.fileEditor.putUserData(DUMB_AWARE, true) // Needed for composite not to postpone loading via DumbService.wrapGently()
    val editorProvider = (editorInfo as LightEditorInfoImpl).provider
    val editorWithProvider = FileEditorWithProvider(editorInfo.getFileEditor(), editorProvider)
    return createCompositeInstance(editorInfo.getFile(), java.util.List.of(editorWithProvider))!!
  }

  override fun getComposite(file: VirtualFile): EditorComposite? {
    val editorManager = LightEditService.getInstance().editorManager as LightEditorManagerImpl
    val openEditorInfo = editorManager.findOpen(file)
    if (openEditorInfo == null) return null
    return LightEditUtil.findEditorComposite(openEditorInfo.fileEditor)
  }

  override fun getComposite(editor: FileEditor): EditorComposite? {
    return LightEditUtil.findEditorComposite(editor)
  }

  override fun getAllEditors(file: VirtualFile): Array<FileEditor> {
    return ContainerUtil.map(LightEditService.getInstance().editorManager.getEditors(file)) { obj: LightEditorInfo -> obj.fileEditor }
      .toArray(FileEditor.EMPTY_ARRAY)
  }
}
