/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.openapi.util.UserDataHolder;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface FileEditor extends UserDataHolder {
  /**
   * @see #isModified() 
   */ 
  String PROP_MODIFIED = "modified";
  /**
   * @see #isValid()  
   */ 
  String PROP_VALID = "valid";

  /**
   * @return component which represents editor in the UI.
   * The method should never return <code>null</code>.
   */
  JComponent getComponent();
  
  /**
   * Returns component to be focused when editor is opened. Method should never return null.
   */
  JComponent getPreferredFocusedComponent();

  /**
   * @return editor's name, a string that identifies editor among
   * other editors. For example, UI form might have two editor: "GUI Designer"
   * and "Text". So "GUI Designer" can be a name of one editor and "Text"
   * can be a name of other editor. The method should never return <code>null</code>.
   */
  String getName();

  /**
   * @return editor's internal state. Method should never return <code>null</code>.
   */
  FileEditorState getState(FileEditorStateLevel level);

  /**
   * Applies given state to the editor.
   * @param state cannot be null
   */
  void setState(FileEditorState state);
  
  /**
   * @return whether the editor's content is modified in comparision with its file. 
   */
  boolean isModified();
  
  /**
   * @return whether the editor is valid or not. For some reasons
   * editor can become invalid. For example, text editor becomes invalid when its file is deleted.
   */
  boolean isValid();

  /**
   * This method is invoked each time when the editor is selected.
   * This can happen in two cases: editor is selected because the selected file
   * has been changed or editor for the selected file has been changed.
   */
  void selectNotify();

  /**
   * This method is invoked each time when the editor is deselected.
   */
  void deselectNotify();

  /**
   * Removes specified listener
   *
   * @param listener to be added
   */
  void addPropertyChangeListener(PropertyChangeListener listener);

  /**
   * Adds specified listener
   *
   * @param listener to be removed
   */
  void removePropertyChangeListener(PropertyChangeListener listener);

  /**
   * @return highlighter object to perform background analysis and highlighting activities.
   * Return <code>null</code> if no background highlighting activity necessary for this file editor.
   */
  BackgroundEditorHighlighter getBackgroundHighlighter();

  /**
   * The method is optional. Currently is used only by find usages subsystem
   * @return the location of user focus. Typically it's a caret or any other form of selection start.
   */
  FileEditorLocation getCurrentLocation();

  StructureViewModel getStructureViewModel();
}
