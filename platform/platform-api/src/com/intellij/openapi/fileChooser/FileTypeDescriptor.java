// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** @deprecated use {@code FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(...).withExtensionFilter(...)} */
@Deprecated(forRemoval = true)
public class FileTypeDescriptor extends FileChooserDescriptor {
  public FileTypeDescriptor(@NlsContexts.DialogTitle String title, String @NotNull ... extensions) {
    super(true, false, false, true, false, false);
    withTitle(title);
    if (extensions.length == 1) {
      withExtensionFilter(extensions[0]);
    }
    else {
      withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", extensions[0].toUpperCase(Locale.ROOT)), extensions);
    }
  }
}
