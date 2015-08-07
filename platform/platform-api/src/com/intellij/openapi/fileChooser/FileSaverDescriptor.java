/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Defines save dialog behaviour
 *
 * @author Konstantin Bulenkov
 * @see FileSaverDialog
 * @see FileChooserDescriptor
 * @since 9.0
 */
public class FileSaverDescriptor extends FileChooserDescriptor implements Cloneable {
  private final List<String> extensions;

  /**
   * Constructs save dialog properties
   *
   * @param title save dialog text title (not window title)
   * @param description description
   * @param extensions accepted file extensions: "txt", "jpg", etc. Accepts all if empty
   */
  public FileSaverDescriptor(@Nls(capitalization = Nls.Capitalization.Title) @NotNull String title,
                             @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description, String... extensions) {
    super(true, true, true, true, false, false);
    setTitle(title);
    setDescription(description);
    this.extensions = Arrays.asList(extensions);
  }

  @Override
  public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
    return extensions.isEmpty() || file.isDirectory() ?
           super.isFileVisible(file, showHiddenFiles)
           :
           extensions.contains(file.getExtension());
  }

  /**
   * Returns accepted file extensions
   *
   * @return accepted file extensions
   */
  public String[] getFileExtensions() {
    return ArrayUtil.toStringArray(extensions);
  }
}
