/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PerspectiveFileEditorProvider extends WeighedFileEditorProvider {
  @NotNull
  public abstract PerspectiveFileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file);

  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return new FileEditorState() {
      public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
        return true;
      }
    };
  }

  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
  }

  @NotNull
  @NonNls
  public final String getEditorTypeId() {
    return getComponentName();
  }

  @NotNull
  public final FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }

  @NonNls
  public final String getComponentName() {
    return getClass().getName();
  }

}
