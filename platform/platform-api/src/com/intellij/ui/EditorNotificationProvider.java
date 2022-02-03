// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
 * Register in the {@code com.intellij.editorNotificationProvider} extension point, see {@link #EP_NAME}.
 * </p>
 */
@ApiStatus.Experimental
public interface EditorNotificationProvider {

  ProjectExtensionPointName<EditorNotificationProvider> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.editorNotificationProvider");

  Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> CONST_NULL = __ -> null;

  @RequiresReadLock
  @NotNull Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                         @NotNull VirtualFile file);
}
