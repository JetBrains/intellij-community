/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface FileEditorProvider {
  /**
   * @param file file to be tested for acceptance. This
   * parameter is never <code>null</code>.
   *
   * @return whether the provider can create valid editor for the specified
   * <code>file</code> or not
   */
  boolean accept(Project project, VirtualFile file);

  /**
   * Creates editor for the specified file. This method
   * is called only if the provider has accepted this file (i.e. method {@link #accept(Project, VirtualFile)} returned 
   * <code>true</code>).
   * The provider should return only valid editor.
   *
   * @return created editor for specified file. This method should never return <code>null</code>.
   */
  FileEditor createEditor(Project project, VirtualFile file);

  /**
   * Disposes the specified <code>editor</code>. It is guaranteed that this method is invoked only for editors 
   * created with this provider.
   *
   * @param editor editor to be disposed. This parameter is always not <code>null</code>.
   */
  void disposeEditor(FileEditor editor);

  /**
   * Deserializes state from the specified <code>sourceElemet</code>
   */
  FileEditorState readState(Element sourceElement, Project project, VirtualFile file);

  /**
   * Serializes state into the specified <code>targetElement</code>
   */
  void writeState(FileEditorState state, Project project, Element targetElement);
  
  /**
   * @return id of type of the editors that are created with this FileEditorProvider. Each FileEditorProvider should have 
   * unique non null id. The id is used for saving/loading of EditorStates.
   */
  String getEditorTypeId();

  /**
   * @return policy that specifies how show editor created via this provider be opened
   *
   * @see FileEditorPolicy#NONE
   * @see FileEditorPolicy#HIDE_DEFAULT_EDITOR
   * @see FileEditorPolicy#PLACE_BEFORE_DEFAULT_EDITOR
   */
  FileEditorPolicy getPolicy();
}