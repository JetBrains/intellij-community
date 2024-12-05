// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsContexts.Label;
import org.jetbrains.annotations.NotNull;

/**
 * Defines save dialog behaviour.
 *
 * @see FileSaverDialog
 * @see FileChooserDescriptor
 */
public class FileSaverDescriptor extends FileChooserDescriptor implements Cloneable {
  /**
   * Constructs save dialog properties.
   *
   * @param title save dialog text title (not window title)
   * @param description description
   * @param extensions accepted file extensions: "txt", "jpg", etc. Accepts all if empty
   */
  public FileSaverDescriptor(@DialogTitle @NotNull String title, @Label @NotNull String description, String... extensions) {
    super(true, true, true, true, false, false);
    setTitle(title);
    setDescription(description);
    if (extensions.length == 1) {
      withExtensionFilter(extensions[0]);
    }
    else if (extensions.length != 0) {
      withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", extensions[0]), extensions);
    }
  }
}
