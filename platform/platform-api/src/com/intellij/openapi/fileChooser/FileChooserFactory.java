// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTextField;
import java.awt.Component;
import java.util.Arrays;

public abstract class FileChooserFactory {
  public static FileChooserFactory getInstance() {
    return ApplicationManager.getApplication().getService(FileChooserFactory.class);
  }

  public abstract @NotNull FileChooserDialog createFileChooser(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Project project,
    @Nullable Component parent
  );

  public abstract @NotNull PathChooserDialog createPathChooser(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Project project,
    @Nullable Component parent
  );

  /**
   * Creates a file chooser that returns the chosen files as {@link java.nio.file.Path} values.
   * <p>
   * The default implementation throws {@link UnsupportedOperationException}
   */
  @ApiStatus.Experimental
  public @NotNull NioFileChooserDialog createNioFileChooser(
    @NotNull FileChooserDescriptor descriptor,
    @Nullable Project project,
    @Nullable Component parent
  ) {
    throw new UnsupportedOperationException("NioFileChooser is not available");
  }

  /**
   * Creates Save File dialog.
   *
   * @param descriptor dialog descriptor
   * @param project    chooser options
   * @return Save File dialog
   */
  public abstract @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project);

  public abstract @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent);

  public abstract @NotNull FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent);

  public @NotNull FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, @Nullable Disposable parent) {
    return createFileTextField(descriptor, true, parent);
  }

  /**
   * Adds a path completion listener to a given text field.
   *
   * @param field      input field to add completion to
   * @param descriptor chooser options
   * @param showHidden include hidden files in completion variants
   * @param parent     when {@code null}, will be registered with {@link com.intellij.openapi.actionSystem.PlatformDataKeys#UI_DISPOSABLE}
   */
  public abstract void installFileCompletion(
    @NotNull JTextField field,
    @NotNull FileChooserDescriptor descriptor,
    boolean showHidden,
    @Nullable Disposable parent
  );
}
