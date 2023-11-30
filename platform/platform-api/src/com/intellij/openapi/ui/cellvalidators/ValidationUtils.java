// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.cellvalidators;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ValidationUtils {
  private ValidationUtils() {}

  public static final ExtendableTextComponent.Extension ERROR_EXTENSION =
    ExtendableTextComponent.Extension.create(AllIcons.General.BalloonError, null, null);

  public static final ExtendableTextComponent.Extension WARNING_EXTENSION =
    ExtendableTextComponent.Extension.create(AllIcons.General.BalloonWarning, null, null);

  public static void setExtension(@NotNull ExtendableTextComponent editor, @NotNull ExtendableTextComponent.Extension extension, boolean set) {
    if (set) {
      editor.addExtension(extension);
    } else {
      editor.removeExtension(extension);
    }
  }

  public static void setExtension(@NotNull ExtendableTextComponent editor, @Nullable ValidationInfo vi) {
    if (vi == null) {
      editor.removeExtension(ERROR_EXTENSION);
      editor.removeExtension(WARNING_EXTENSION);
    } else if (vi.warning) {
      editor.addExtension(WARNING_EXTENSION);
    } else {
      editor.addExtension(ERROR_EXTENSION);
    }
  }
}
