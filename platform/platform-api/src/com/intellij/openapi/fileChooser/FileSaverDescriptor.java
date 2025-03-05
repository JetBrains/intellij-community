// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsContexts.Label;
import org.jetbrains.annotations.NotNull;

/**
 * Defines save dialog behaviour.
 *
 * @see FileSaverDialog
 * @see FileChooserDescriptor
 */
public class FileSaverDescriptor extends FileChooserDescriptor {
  /**
   * Constructs save dialog properties.
   *
   * @param title save dialog text title (not window title)
   * @param description description
   */
  public FileSaverDescriptor(@DialogTitle @NotNull String title, @Label @NotNull String description) {
    super(true, true, true, true, false, false);
    setTitle(title);
    setDescription(description);
  }

  /**
   * Constructs save dialog properties.
   *
   * @param title save dialog text title (not window title)
   * @param description description
   * @param extension accepted file extension ("txt", "jpg", etc.)
   */
  public FileSaverDescriptor(@DialogTitle @NotNull String title, @Label @NotNull String description, String extension) {
    this(title, description);
    withExtensionFilter(extension);
  }

  /**
   * Constructs save dialog properties.
   *
   * @param fileChooserDescriptor FileChooserDescriptor to be copied
   */
  public FileSaverDescriptor(@NotNull FileChooserDescriptor fileChooserDescriptor) {
    super(fileChooserDescriptor);
  }
  /**
   * Use {@link FileSaverDescriptorFactory}.
   */
  FileSaverDescriptor(
    boolean chooseFiles,
    boolean chooseFolders,
    boolean chooseJarContents,
    boolean chooseMultiple
  ) {
    super(chooseFiles, chooseFolders, chooseJarContents, chooseMultiple);
  }

  @Override
  public FileSaverDescriptor withTitle(@NlsContexts.DialogTitle String title) {
    super.withTitle(title);
    return this;
  }

  @Override
  public FileSaverDescriptor withDescription(String description) {
    super.withDescription(description);
    return this;
  }

  /**
   * @deprecated this constructor variant doesn't show the correct label in the Windows native dialog;
   * use {@link #FileSaverDescriptor(String, String)} with {@link #withExtensionFilter(String, String...)} instead
   */
  @Deprecated
  public FileSaverDescriptor(@DialogTitle @NotNull String title, @Label @NotNull String description, String... extensions) {
    this(title, description);
    if (extensions.length == 1) {
      withExtensionFilter(extensions[0]);
    }
    else if (extensions.length != 0) {
      withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", extensions[0]), extensions);
    }
  }
}
