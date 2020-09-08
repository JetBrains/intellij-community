// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileChooser;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Defines save dialog behaviour
 *
 * @author Konstantin Bulenkov
 * @see FileSaverDialog
 * @see FileChooserDescriptor
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
  public FileSaverDescriptor(@DialogTitle @NotNull String title,
                             @NlsContexts.Label @NotNull String description, String... extensions) {
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
  public @NlsSafe String[] getFileExtensions() {
    return ArrayUtilRt.toStringArray(extensions);
  }
}
