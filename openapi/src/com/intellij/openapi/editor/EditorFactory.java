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
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services for creating document and editor instances.
 */
public abstract class EditorFactory implements ApplicationComponent {
  /**
   * Returns the editor factory instance.
   *
   * @return the editor factory instance.
   */
  public static EditorFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(EditorFactory.class);
  }

  /**
   * Creates a document from the specified text specified as a character sequence.
   *
   * @param text the text to create the document from.
   * @return the document instance.
   */
  @NotNull
  public abstract Document createDocument(CharSequence text);

  /**
   * Creates a document from the specified text specified as an array of characters.
   *
   * @param text the text to create the document from.
   * @return the document instance.
   */
  @NotNull
  public abstract Document createDocument(char[] text);

  /**
   * Creates an editor for the specified document.
   *
   * @param document the document to create the editor for.
   * @return the editor instance.
   * @see #releaseEditor(Editor)
   */
  public abstract Editor createEditor(Document document);

  /**
   * Creates a read-only editor for the specified document.
   *
   * @param document the document to create the editor for.
   * @return the editor instance.
   * @see #releaseEditor(Editor)
   */
  public abstract Editor createViewer(Document document);

  /**
   * Creates an editor for the specified document associated with the specified project.
   *
   * @param document the document to create the editor for.
   * @param project the project with which the editor is associated.
   * @return the editor instance.
   * @see Editor#getProject()
   * @see #releaseEditor(Editor)
   */
  public abstract Editor createEditor(Document document, @Nullable Project project);

  /**
   * Creates a read-only editor for the specified document associated with the specified project.
   *
   * @param document the document to create the editor for.
   * @param project the project with which the editor is associated.
   * @return the editor instance.
   * @see Editor#getProject()
   * @see #releaseEditor(Editor)
   */
  public abstract Editor createViewer(Document document, @Nullable Project project);

  /**
   * Disposes of the specified editor instance.
   *
   * @param editor the editor instance to release.
   */
  public abstract void releaseEditor(Editor editor);

  /**
   * Returns the list of editors for the specified document associated with the specified project.
   *
   * @param document the document for which editors are requested.
   * @param project  the project with which editors should be associated, or null if any editors
   *                 for this document should be returned.
   * @return the list of editors.
   */
  public abstract Editor[] getEditors(Document document, @Nullable Project project);

  /**
   * Returns the list of all editors for the specified document.
   *
   * @param document the document for which editors are requested.
   * @return the list of editors.
   */
  public abstract Editor[] getEditors(Document document);

  /**
   * Returns the list of all currently open editors.
   *
   * @return the list of editors.
   */
  public abstract Editor[] getAllEditors();

  /**
   * Registers a listener for receiving notifications when editor instances are created
   * and released.
   *
   * @param listener the listener instance.
   */
  public abstract void addEditorFactoryListener(EditorFactoryListener listener);

  /**
   * Unregisters a listener for receiving notifications when editor instances are created
   * and released.
   *
   * @param listener the listener instance.
   */
  public abstract void removeEditorFactoryListener(EditorFactoryListener listener);

  /**
   * Returns the service for attaching event listeners to all editor instances.
   *
   * @return the event multicaster instance.
   */
  @NotNull
  public abstract EditorEventMulticaster getEventMulticaster();

  /**
   * Reloads the editor settings and refrehes all currently open editors.
   */
  public abstract void refreshAllEditors();
}
