// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.diff;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public enum DirDiffOperation {
  COPY_TO, COPY_FROM, MERGE, EQUAL, NONE, DELETE;

  public Icon getIcon() {
    return switch (this) {
      case COPY_TO -> AllIcons.Vcs.Arrow_right;
      case COPY_FROM -> AllIcons.Vcs.Arrow_left;
      case MERGE -> AllIcons.Vcs.Not_equal;
      case EQUAL -> AllIcons.Vcs.Equal;
      case DELETE -> AllIcons.Vcs.Remove;
      case NONE -> JBUIScale.scaleIcon(EmptyIcon.create(16));
    };
  }

  public @Nullable Color getTextColor() {
    return switch (this) {
      case COPY_TO, COPY_FROM -> FileStatus.ADDED.getColor();
      case MERGE -> FileStatus.MODIFIED.getColor();
      case DELETE -> FileStatus.DELETED.getColor();
      case EQUAL, NONE -> JBColor.foreground();
    };
  }
}
