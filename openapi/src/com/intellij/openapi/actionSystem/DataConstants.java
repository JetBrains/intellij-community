/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

public interface DataConstants {
  /**
   * Returns com.intellij.openapi.project.Project
   */
  String PROJECT = "project";

  /**
   * Returns com.intellij.openapi.module.Module
   */
  String MODULE = "module";

  /**
   * Returns com.intellij.openapi.vfs.VirtualFile
   */
  String VIRTUAL_FILE = "virtualFile";

  /**
   * Returns array of com.intellij.openapi.vfs.VirtualFile
   */
  String VIRTUAL_FILE_ARRAY = "virtualFileArray";

  /**
   * Returns com.intellij.openapi.editor.Editor
   */
  String EDITOR = "editor";

  /**
   * Returns com.intellij.openapi.fileEditor.FileEditor
   */
  String FILE_EDITOR = "fileEditor";

  /**
   * Returns com.intellij.openapi.fileEditor.OpenFileDescriptor
   * @deprecated {@link #NAVIGATABLE} should be used instead
   */
  String OPEN_FILE_DESCRIPTOR = "openFileDescriptor";

  /**
   * Returns the text of currently selected file/file revision
   */
  String FILE_TEXT = "fileText";

  /**
   * Returns Boolean.TRUE if action is executed in modal context and
   * Boolean.FALSE if action is executed not in modal context. If context
   * is unknown then the value of this data constant is <code>null</code>.
   */
  String IS_MODAL_CONTEXT = "isModalContext";

  /**
   * Returns {@link com.intellij.openapi.diff.DiffViewer}
   */
  String DIFF_VIEWER = "diffViewer";
  /**
   * Returns help id (String)
   */  
  String HELP_ID = "helpId";
  /**
   * Returns project if project node is selected (in project view)
   */
  String PROJECT_CONTEXT = "context.Project";
  /**
   * Returns module if module node is selected (in module view)
   */
  String MODULE_CONTEXT = "context.Module";
  String MODULE_CONTEXT_ARRAY = "context.Module.Array";

  /**
   * Returns {@link com.intellij.pom.Navigatable}
   */
  String NAVIGATABLE = "Navigatable";
  /**
   * Returns ExporterToTextFile
   */
  String EXPORTER_TO_TEXT_FILE = "exporterToTextFile";
  /**
   * Returns com.intellij.psi.PsiElement
   */
  String PSI_ELEMENT = "psi.Element";
  /**
   * Returns com.intellij.psi.PsiFile
   */
  String PSI_FILE = "psi.File";
}
