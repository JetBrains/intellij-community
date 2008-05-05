package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class HttpFileEditorProvider implements FileEditorProvider {
  public boolean accept(@NotNull final Project project, @NotNull final VirtualFile file) {
    return file instanceof HttpVirtualFile && !file.isDirectory();
  }

  @NotNull
  public FileEditor createEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    HttpVirtualFile virtualFile = (HttpVirtualFile)file;
    RemoteFileInfo remoteFileInfo = virtualFile.getFileInfo();
    if (remoteFileInfo.isDownloaded()) {
      return TextEditorProvider.getInstance().createEditor(project, file);
    }
    return new HttpFileEditor(project, virtualFile); 
  }

  public void disposeEditor(@NotNull final FileEditor editor) {
  }

  @NotNull
  public FileEditorState readState(@NotNull final Element sourceElement, @NotNull final Project project, @NotNull final VirtualFile file) {
    return new TextEditorState();
  }

  public void writeState(@NotNull final FileEditorState state, @NotNull final Project project, @NotNull final Element targetElement) {
  }

  @NotNull
  public String getEditorTypeId() {
    return "httpFileEditor";
  }

  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
