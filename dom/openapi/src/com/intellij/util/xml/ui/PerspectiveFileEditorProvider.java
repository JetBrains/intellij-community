/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Disposer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PerspectiveFileEditorProvider extends WeighedFileEditorProvider implements ApplicationComponent {
  @NotNull
  public abstract PerspectiveFileEditor createEditor(Project project, VirtualFile file);

  public void disposeEditor(FileEditor editor) {
    Disposer.dispose((PerspectiveFileEditor)editor);
  }

  @NotNull
  public FileEditorState readState(Element sourceElement, Project project, VirtualFile file) {
    return new FileEditorState() {
      public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
        return true;
      }
    };
  }

  public void writeState(FileEditorState state, Project project, Element targetElement) {
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

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
