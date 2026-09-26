// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.event;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Allows receiving notifications when editors are created and released.
 * To subscribe, use the {@code com.intellij.editorFactoryListener} extension point or
 * {@link com.intellij.openapi.editor.EditorFactory#addEditorFactoryListener(EditorFactoryListener, com.intellij.openapi.Disposable)}.
 * <p>
 * This listener is application level and is triggered for editors of ALL OPEN PROJECTS.
 */
public interface EditorFactoryListener extends EventListener {
  /**
   * Called after {@link com.intellij.openapi.editor.Editor} instance has been created for any open project.
   */
  default void editorCreated(@NotNull EditorFactoryEvent event) {
  }

  /**
   * Called before {@link com.intellij.openapi.editor.Editor} instance will be released for any open project.
   */
  default void editorReleased(@NotNull EditorFactoryEvent event) {
  }
}

