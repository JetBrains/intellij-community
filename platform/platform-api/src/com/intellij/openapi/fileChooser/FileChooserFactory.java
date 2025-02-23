// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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
