// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Provides services for creating document and editor instances.
 *
 * Creating and releasing of editors must be done from EDT.
 */
public abstract class EditorFactory {
  /**
   * Returns the editor factory instance.
   *
   * @return the editor factory instance.
   */
  public static EditorFactory getInstance() {
    return ApplicationManager.getApplication().getService(EditorFactory.class);
  }

  /**
   * Creates a document from the specified text specified as a character sequence.
   */
  @NotNull
  public abstract Document createDocument(@NotNull CharSequence text);

  /**
   * Creates a document from the specified text specified as an array of characters.
   */
  @NotNull
  public abstract Document createDocument(char @NotNull [] text);

  /**
   * Creates an editor for the specified document. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   */
  public abstract Editor createEditor(@NotNull Document document);

  /**
   * Creates a read-only editor for the specified document. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   */
  public abstract Editor createViewer(@NotNull Document document);

  /**
   * Creates an editor for the specified document associated with the specified project. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   * @see Editor#getProject()
   */
  public abstract Editor createEditor(@NotNull Document document, @Nullable Project project);

  /**
   * Does the same as {@link #createEditor(Document, Project)} and also sets the special kind for the created editor
   */
  public abstract Editor createEditor(@NotNull Document document, @Nullable Project project, @NotNull EditorKind kind);

  /**
   * Creates an editor for the specified document associated with the specified project. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   *
   * @param document the document to create the editor for.
   * @param project  the project for which highlighter should be created
   * @param fileType the file type according to which the editor contents is highlighted.
   * @param isViewer true if read-only editor should be created
   * @see Editor#getProject()
   */
  public abstract Editor createEditor(@NotNull Document document, Project project, @NotNull FileType fileType, boolean isViewer);

  /**
   * Creates an editor for the specified document associated with the specified project. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   * @param document the document to create the editor for.
   * @param project  the project for which highlighter should be created
   * @param file     the file according to which the editor contents is highlighted.
   * @param isViewer true if read-only editor should be created
   * @return the editor instance.
   * @see Editor#getProject()
   */
  public abstract Editor createEditor(@NotNull Document document, Project project, @NotNull VirtualFile file, boolean isViewer);

  /**
   * Does the same as {@link #createEditor(Document, Project, VirtualFile, boolean)} and also sets the special kind for the created editor
   */
  public abstract Editor createEditor(@NotNull Document document, Project project, @NotNull VirtualFile file, boolean isViewer,
                                      @NotNull EditorKind kind);

  /**
   * Creates a read-only editor for the specified document associated with the specified project. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}
   * </p>
   */
  public abstract Editor createViewer(@NotNull Document document, @Nullable Project project);

  /**
   * Does the same as {@link #createViewer(Document, Project)} and also sets the special kind for the created viewer
   */
  public abstract Editor createViewer(@NotNull Document document, @Nullable Project project, @NotNull EditorKind kind);

  /**
   * Disposes the specified editor instance. Must be invoked in EDT.
   */
  public abstract void releaseEditor(@NotNull Editor editor);

  /**
   * Returns the stream of editors for the specified document associated with the specified project.
   *
   * @param document the document for which editors are requested.
   * @param project  the project with which editors should be associated, or null if any editors
   *                 for this document should be returned.
   */
  public abstract @NotNull Stream<Editor> editors(@NotNull Document document, @Nullable Project project);

  /**
   * Returns the stream of all editors for the specified document.
   */
  public final @NotNull Stream<Editor> editors(@NotNull Document document) {
    return editors(document, null);
  }

  /**
   * Consider using {@link #editors(Document, Project)}.
   */
  public final Editor @NotNull [] getEditors(@NotNull Document document, @Nullable Project project) {
    return editors(document, project).toArray(Editor[]::new);
  }

  /**
   * Consider using {@link #editors(Document)}.
   */
  public final Editor @NotNull [] getEditors(@NotNull Document document) {
    return getEditors(document, null);
  }

  /**
   * Returns the list of all currently open editors.
   */
  public abstract Editor @NotNull [] getAllEditors();

  /**
   * Registers a listener for receiving notifications when editor instances are created
   * and released.
   * @deprecated use the {@link #addEditorFactoryListener(EditorFactoryListener, Disposable)} instead
   */
  @Deprecated(forRemoval = true)
  public abstract void addEditorFactoryListener(@NotNull EditorFactoryListener listener);

  /**
   * Registers a listener for receiving notifications when editor instances are created and released
   * and removes the listener when the {@code parentDisposable} gets disposed.
   */
  public abstract void addEditorFactoryListener(@NotNull EditorFactoryListener listener, @NotNull Disposable parentDisposable);

  /**
   * Un-registers a listener for receiving notifications when editor instances are created
   * and released.
   * @deprecated you should have used the {@link #addEditorFactoryListener(EditorFactoryListener, Disposable)} instead
   */
  @Deprecated(forRemoval = true)
  public abstract void removeEditorFactoryListener(@NotNull EditorFactoryListener listener);

  /**
   * Returns the service for attaching event listeners to all editor instances.
   */
  @NotNull
  public abstract EditorEventMulticaster getEventMulticaster();

  /**
   * Reloads the editor settings and refreshes all currently open editors.
   */
  public abstract void refreshAllEditors();
}
