// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public abstract FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent);

  @NotNull
  public abstract PathChooserDialog createPathChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent);

  /**
   * Creates Save File dialog.
   *
   * @param descriptor dialog descriptor
   * @param project    chooser options
   * @return Save File dialog
   */
  @NotNull
  public abstract FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project);

  @NotNull
  public abstract FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent);

  @NotNull
  public abstract FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent);

  @NotNull
  public FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, @Nullable Disposable parent) {
    return createFileTextField(descriptor, true, parent);
  }

  /**
   * Adds path completion listener to a given text field.
   *
   * @param field      input field to add completion to
   * @param descriptor chooser options
   * @param showHidden include hidden files into completion variants
   * @param parent     if null then will be registered with {@link com.intellij.openapi.actionSystem.PlatformDataKeys#UI_DISPOSABLE}
   */
  public abstract void installFileCompletion(@NotNull JTextField field,
                                             @NotNull FileChooserDescriptor descriptor,
                                             boolean showHidden,
                                             @Nullable Disposable parent);
}
