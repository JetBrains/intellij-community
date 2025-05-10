// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.ide.lightEdit.project

import com.intellij.ide.lightEdit.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorComposite.Companion.EMPTY
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorCompositeModel
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.toArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import org.jdom.Element

internal class LightEditFileEditorManagerImpl(
  project: Project,
  coroutineScope: CoroutineScope,
) : LightEditFileEditorManagerBase(project = project, coroutineScope = coroutineScope) {
  override fun loadState(state: Element) {
    // do not open previously opened files
  }

  override fun getState(): Element? = null

  override fun openFileImpl2(window: EditorWindow, file: VirtualFile, options: FileEditorOpenOptions): FileEditorComposite {
    LightEditService.getInstance().openFile(file)
    val data = getSelectedEditorWithProvider(file) ?: return EMPTY
    return object : FileEditorComposite {
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
    if (data == null) {
      return Pair(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY)
    }
    else {
      return Pair(arrayOf(data.fileEditor), arrayOf(data.provider))
    }
  }

  override fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider? {
    val editorManager = LightEditService.getInstance().editorManager as LightEditorManagerImpl
    val editorInfo = editorManager.findOpen(file) as LightEditorInfoImpl? ?: return null
    return FileEditorWithProvider(editorInfo.fileEditor, editorInfo.provider)
  }

  override fun getSelectedEditor(): FileEditor? = LightEditService.getInstance().selectedFileEditor

  override fun getSelectedTextEditor(): Editor? = LightEditorInfoImpl.getEditor(selectedEditor)

  override fun getOpenFiles(): Array<VirtualFile> {
    return VfsUtilCore.toVirtualFileArray(LightEditService.getInstance().editorManager.openFiles)
  }

  override fun isFileOpen(file: VirtualFile): Boolean = LightEditService.getInstance().editorManager.isFileOpen(file)

  override fun hasOpenedFile(): Boolean = !LightEditService.getInstance().editorManager.openFiles.isEmpty()

  override fun getSelectedFiles(): Array<VirtualFile> {
    return arrayOf(LightEditService.getInstance().selectedFile ?: return VirtualFile.EMPTY_ARRAY)
  }

  override fun getCurrentFile(): VirtualFile? {
    return LightEditService.getInstance().selectedFile
  }

  override fun hasOpenFiles(): Boolean = !LightEditService.getInstance().editorManager.openFiles.isEmpty()

  fun createEditorComposite(editorInfo: LightEditorInfo): EditorComposite {
    // Needed for composite not to postpone loading via DumbService.wrapGently()
    editorInfo.fileEditor.putUserData(FileEditorManagerKeys.DUMB_AWARE, true)
    val editorProvider = (editorInfo as LightEditorInfoImpl).provider
    val editorWithProvider = FileEditorWithProvider(editorInfo.getFileEditor(), editorProvider)
    return createCompositeInstance(
      file = editorInfo.getFile(),
      model = flowOf(EditorCompositeModel(fileEditorAndProviderList = java.util.List.of(editorWithProvider), state = null)),
      coroutineScope = coroutineScope.childScope(editorInfo.toString()),
    )!!
  }

  override fun getComposite(file: VirtualFile): EditorComposite? {
    val editorManager = LightEditService.getInstance().editorManager as LightEditorManagerImpl
    val openEditorInfo = editorManager.findOpen(file) ?: return null
    return LightEditUtil.findEditorComposite(openEditorInfo.fileEditor)
  }

  override fun getComposite(editor: FileEditor): EditorComposite? = LightEditUtil.findEditorComposite(editor)

  override fun getAllEditors(file: VirtualFile): Array<FileEditor> {
    return LightEditService.getInstance().editorManager.getEditors(file).map { it.fileEditor }.toArray(FileEditor.EMPTY_ARRAY)
  }
}
