// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * Provides services for creating document and editor instances.
 * <p>
 * Creating and releasing of editors must be done from EDT.
 */
@ApiStatus.NonExtendable
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
  public abstract @NotNull Document createDocument(@NotNull CharSequence text);

  /**
   * Creates a document from the specified text specified as an array of characters.
   */
  public abstract @NotNull Document createDocument(char @NotNull [] text);

  /**
   * Creates an empty document.
   *
   * @param allowUpdatesWithoutWriteAction {@code true} if the document should allow updates without write action; by default, the global
   *                                       <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">read-write lock</a> is
   *                                       used to protect the content of a document.
   */
  public abstract @NotNull Document createDocument(boolean allowUpdatesWithoutWriteAction);

  /**
   * Creates a document from the specified text specified as a char sequence.
   *
   * @param text                           the text to create the document for.
   * @param acceptsSlashR                  {@code true} if the document should accept '\r' as a line separator; by default, content of the
   *                                       document is supposed to use '\n' as a line separator, and it's checked at runtime.
   * @param allowUpdatesWithoutWriteAction {@code true} if the document should allow updates without write action; by default, the global
   *                                       <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">read-write lock</a> is
   *                                       used to protect the content of a document.
   * @return the document instance.
   */
  public abstract @NotNull Document createDocument(@NotNull CharSequence text,
                                                   boolean acceptsSlashR,
                                                   boolean allowUpdatesWithoutWriteAction);

  /**
   * Creates an editor for the specified document. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   */
  @RequiresEdt
  public abstract Editor createEditor(@NotNull Document document);

  /**
   * Creates a read-only editor for the specified document. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   */
  @RequiresEdt
  public abstract Editor createViewer(@NotNull Document document);

  /**
   * Creates an editor for the specified document associated with the specified project. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}.
   * </p>
   * @see Editor#getProject()
   */
  @RequiresEdt
  public abstract Editor createEditor(@NotNull Document document, @Nullable Project project);

  /**
   * Does the same as {@link #createEditor(Document, Project)} and also sets the special kind for the created editor
   */
  @RequiresEdt
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
  @RequiresEdt
  public abstract Editor createEditor(@NotNull Document document, @Nullable Project project, @NotNull FileType fileType, boolean isViewer);

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
  @RequiresEdt
  public abstract Editor createEditor(@NotNull Document document, @Nullable Project project, @NotNull VirtualFile file, boolean isViewer);

  /**
   * Does the same as {@link #createEditor(Document, Project, VirtualFile, boolean)} and also sets the special kind for the created editor
   */
  @RequiresEdt
  public abstract Editor createEditor(@NotNull Document document, @Nullable Project project, @NotNull VirtualFile file, boolean isViewer,
                                      @NotNull EditorKind kind);

  /**
   * Creates a read-only editor for the specified document associated with the specified project. Must be invoked in EDT.
   * <p>
   * The created editor must be disposed after use by calling {@link #releaseEditor(Editor)}
   * </p>
   */
  @RequiresEdt
  public abstract Editor createViewer(@NotNull Document document, @Nullable Project project);

  /**
   * Does the same as {@link #createViewer(Document, Project)} and also sets the special kind for the created viewer
   */
  @RequiresEdt
  public abstract Editor createViewer(@NotNull Document document, @Nullable Project project, @NotNull EditorKind kind);

  /**
   * Disposes the specified editor instance. Must be invoked in EDT.
   */
  @RequiresEdt
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

  public abstract @NotNull List<Editor> getEditorList();

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
  public abstract @NotNull EditorEventMulticaster getEventMulticaster();

  /**
   * Reloads the editor settings and refreshes all currently open editors.
   */
  @RequiresEdt
  public abstract void refreshAllEditors();
}
