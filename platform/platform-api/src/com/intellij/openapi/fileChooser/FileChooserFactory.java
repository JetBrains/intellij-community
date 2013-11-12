/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class FileChooserFactory {
  public static FileChooserFactory getInstance() {
    return ServiceManager.getService(FileChooserFactory.class);
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
   * @since 9.0
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
