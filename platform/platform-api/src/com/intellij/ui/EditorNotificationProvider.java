// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * Adds custom notification/UI to the top of file editors.
 * <p>
 * During indexing only {@link com.intellij.openapi.project.DumbAware} instances are shown.
 * </p>
 * <p>
 * Register in the {@code com.intellij.editorNotificationProvider} extension point, see {@link #EP_PROJECT}.
 * </p>
 *
 * @param <C> the type of the notification UI component, see also {@link EditorNotificationPanel}
 */
@ApiStatus.Experimental
public interface EditorNotificationProvider<C extends JComponent> {

  ProjectExtensionPointName<EditorNotificationProvider<?>> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.editorNotificationProvider");

  @FunctionalInterface
  interface ComponentProvider<C extends JComponent> extends Function<FileEditor, C> {

    ComponentProvider<JComponent> DUMMY = __ -> null;

    @SuppressWarnings("unchecked")
    static <T extends JComponent> ComponentProvider<T> getDummy() {
      return (ComponentProvider<T>)DUMMY;
    }


    @Override
    @RequiresEdt
    @Nullable C apply(@NotNull FileEditor fileEditor);
  }

  /**
   * A unique key.
   */
  @NotNull Key<C> getKey();

  @RequiresReadLock
  @NotNull ComponentProvider<C> collectNotificationData(@NotNull Project project,
                                                        @NotNull VirtualFile file);
}
