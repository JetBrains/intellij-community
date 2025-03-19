// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public enum ErrorTreeElementKind {
  INFO ("INFO", IdeBundle.message("errortree.information"), AllIcons.General.Information),
  ERROR ("ERROR", IdeBundle.message("errortree.error"), AllIcons.General.Error),
  WARNING ("WARNING", IdeBundle.message("errortree.warning"), AllIcons.General.Warning),
  NOTE ("NOTE", IdeBundle.message("errortree.note"), AllIcons.General.Note),
  GENERIC ("GENERIC", "", null);

  private final String myText;
  private final @Nls String myPresentableText;
  private final Icon myIcon;

  ErrorTreeElementKind(@NonNls String text, @NotNull @Nls String presentableText, @Nullable Icon icon) {
    myText = text;
    myPresentableText = presentableText;
    myIcon = icon;
  }

  @Override
  public String toString() {
    return myText; // for debug purposes
  }

  public @Nls String getPresentableText() {
    return myPresentableText;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public static @NotNull ErrorTreeElementKind convertMessageFromCompilerErrorType(int type) {
    return switch (type) {
      case MessageCategory.ERROR -> ERROR;
      case MessageCategory.WARNING -> WARNING;
      case MessageCategory.INFORMATION, MessageCategory.STATISTICS -> INFO;
      case MessageCategory.SIMPLE -> GENERIC;
      case MessageCategory.NOTE -> NOTE;
      default -> GENERIC;
    };
  }
}
