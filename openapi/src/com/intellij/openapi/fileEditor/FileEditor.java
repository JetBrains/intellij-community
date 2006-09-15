/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 *
 * @see com.intellij.openapi.fileEditor.TextEditor
 */
public interface  FileEditor extends UserDataHolder {
  /**
   * @see #isModified()
   */
  @NonNls String PROP_MODIFIED = "modified";
  /**
   * @see #isValid()
   */
  @NonNls String PROP_VALID = "valid";

  /**
   * @return component which represents editor in the UI.
   * The method should never return <code>null</code>.
   */
  @NotNull
  JComponent getComponent();

  /**
   * Returns component to be focused when editor is opened.
   */
  @Nullable
  JComponent getPreferredFocusedComponent();

  /**
   * @return editor's name, a string that identifies editor among
   * other editors. For example, UI form might have two editor: "GUI Designer"
   * and "Text". So "GUI Designer" can be a name of one editor and "Text"
   * can be a name of other editor. The method should never return <code>null</code>.
   */
  @NonNls @NotNull
  String getName();

  /**
   * @return editor's internal state. Method should never return <code>null</code>.
   */
  @NotNull
  FileEditorState getState(@NotNull FileEditorStateLevel level);

  /**
   * Applies given state to the editor.
   * @param state cannot be null
   */
  void setState(@NotNull FileEditorState state);

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
  void addPropertyChangeListener(@NotNull PropertyChangeListener listener);

  /**
   * Adds specified listener
   *
   * @param listener to be removed
   */
  void removePropertyChangeListener(@NotNull PropertyChangeListener listener);

  /**
   * @return highlighter object to perform background analysis and highlighting activities.
   * Return <code>null</code> if no background highlighting activity necessary for this file editor.
   */
  @Nullable
  BackgroundEditorHighlighter getBackgroundHighlighter();

  /**
   * The method is optional. Currently is used only by find usages subsystem
   * @return the location of user focus. Typically it's a caret or any other form of selection start.
   */
  FileEditorLocation getCurrentLocation();

  @Nullable
  StructureViewBuilder getStructureViewBuilder();
}
