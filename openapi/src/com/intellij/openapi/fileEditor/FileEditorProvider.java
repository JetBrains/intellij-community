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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Should be implemented by an {@link com.intellij.openapi.components.ApplicationComponent}
 *
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
  boolean accept(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * Creates editor for the specified file. This method
   * is called only if the provider has accepted this file (i.e. method {@link #accept(Project, VirtualFile)} returned 
   * <code>true</code>).
   * The provider should return only valid editor.
   *
   * @return created editor for specified file. This method should never return <code>null</code>.
   */
  @NotNull
  FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * Disposes the specified <code>editor</code>. It is guaranteed that this method is invoked only for editors 
   * created with this provider.
   *
   * @param editor editor to be disposed. This parameter is always not <code>null</code>.
   */
  void disposeEditor(@NotNull FileEditor editor);

  /**
   * Deserializes state from the specified <code>sourceElemet</code>
   */
  @NotNull
  FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file);

  /**
   * Serializes state into the specified <code>targetElement</code>
   */
  void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement);

  /**
   * @return id of type of the editors that are created with this FileEditorProvider. Each FileEditorProvider should have 
   * unique non null id. The id is used for saving/loading of EditorStates.
   */
  @NotNull @NonNls
  String getEditorTypeId();

  /**
   * @return policy that specifies how show editor created via this provider be opened
   *
   * @see FileEditorPolicy#NONE
   * @see FileEditorPolicy#HIDE_DEFAULT_EDITOR
   * @see FileEditorPolicy#PLACE_BEFORE_DEFAULT_EDITOR
   */
  @NotNull
  FileEditorPolicy getPolicy();
}