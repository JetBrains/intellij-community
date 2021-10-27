// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project;

import com.intellij.ide.lightEdit.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LightEditFileEditorManagerImpl extends FileEditorManagerImpl {

  LightEditFileEditorManagerImpl(@NotNull Project project) {
    super(project);
  }

  @Override
  public void loadState(@NotNull Element state) {
    // do not open previously opened files
  }

  @Override
  public @Nullable Element getState() {
    return null;
  }

  @Override
  public @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(@NotNull EditorWindow window,
                                                                         @NotNull VirtualFile file,
                                                                         @NotNull FileEditorOpenOptions options) {
    LightEditService.getInstance().openFile(file);
    return getEditorsWithProviders(file);
  }

  @Override
  public @NotNull Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file) {
    FileEditorWithProvider data = getSelectedEditorWithProvider(file);
    return data != null
           ? Pair.create(new FileEditor[]{data.getFileEditor()}, new FileEditorProvider[]{data.getProvider()})
           : Pair.create(FileEditor.EMPTY_ARRAY, new FileEditorProvider[0]);
  }

  @Override
  public @Nullable FileEditorWithProvider getSelectedEditorWithProvider(@NotNull VirtualFile file) {
    LightEditorManagerImpl editorManager = (LightEditorManagerImpl)LightEditService.getInstance().getEditorManager();
    LightEditorInfoImpl editorInfo = (LightEditorInfoImpl)editorManager.findOpen(file);
    if (editorInfo != null) {
      return new FileEditorWithProvider(editorInfo.getFileEditor(), editorInfo.getProvider());
    }
    return null;
  }

  @Override
  public @Nullable FileEditor getSelectedEditor() {
    return LightEditService.getInstance().getSelectedFileEditor();
  }

  @Override
  public Editor getSelectedTextEditor() {
    return LightEditorInfoImpl.getEditor(getSelectedEditor());
  }

  @Override
  public VirtualFile @NotNull [] getOpenFiles() {
    return VfsUtilCore.toVirtualFileArray(LightEditService.getInstance().getEditorManager().getOpenFiles());
  }

  @Override
  public boolean isFileOpen(@NotNull VirtualFile file) {
    return LightEditService.getInstance().getEditorManager().isFileOpen(file);
  }

  @Override
  public boolean hasOpenedFile() {
    return !LightEditService.getInstance().getEditorManager().getOpenFiles().isEmpty();
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    VirtualFile file = LightEditService.getInstance().getSelectedFile();
    return file != null ? new VirtualFile[] {file} : VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public VirtualFile getCurrentFile() {
    return LightEditService.getInstance().getSelectedFile();
  }

  @Override
  public VirtualFile getFile(@NotNull FileEditor editor) {
    VirtualFile file = editor.getFile();
    if (file != null) {
      return file;
    }
    FileEditorLocation location = editor.getCurrentLocation();
    if (location != null) {
      return location.getEditor().getFile();
    }
    LightEditorManagerImpl editorManager = (LightEditorManagerImpl)LightEditService.getInstance().getEditorManager();
    for (VirtualFile openFile : editorManager.getOpenFiles()) {
      LightEditorInfo editorInfo = editorManager.findOpen(openFile);
      if (editorInfo != null && editorInfo.getFileEditor().equals(editor)) {
        return openFile;
      }
    }
    return null;
  }

  @Override
  public boolean hasOpenFiles() {
    return !LightEditService.getInstance().getEditorManager().getOpenFiles().isEmpty();
  }

  @NotNull
  public EditorWithProviderComposite createEditorComposite(@NotNull LightEditorInfo editorInfo) {
    editorInfo.getFileEditor().putUserData(DUMB_AWARE, true); // Needed for composite not to postpone loading via DumbService.wrapGently()
    FileEditorProvider editorProvider = ((LightEditorInfoImpl)editorInfo).getProvider();
    FileEditorWithProvider editorWithProvider = new FileEditorWithProvider(editorInfo.getFileEditor(), editorProvider);
    EditorWithProviderComposite composite = createComposite(editorInfo.getFile(), List.of(editorWithProvider));
    assert composite != null;
    return composite;
  }

  @Override
  protected @Nullable EditorComposite getComposite(@NotNull FileEditor editor) {
    return LightEditUtil.findEditorComposite(editor);
  }

  @Override
  public FileEditor @NotNull [] getAllEditors(@NotNull VirtualFile file) {
    return ContainerUtil.map(LightEditService.getInstance().getEditorManager()
                                             .getEditors(file), editorInfo -> editorInfo.getFileEditor())
                        .toArray(FileEditor.EMPTY_ARRAY);
  }
}
