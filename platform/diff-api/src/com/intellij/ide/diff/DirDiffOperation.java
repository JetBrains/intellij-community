// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    switch (this) {
      case COPY_TO:   return AllIcons.Vcs.Arrow_right;
      case COPY_FROM: return AllIcons.Vcs.Arrow_left;
      case MERGE:     return AllIcons.Vcs.Not_equal;
      case EQUAL:     return AllIcons.Vcs.Equal;
      case DELETE:    return AllIcons.Vcs.Remove;
      case NONE:
    }
    return JBUIScale.scaleIcon(EmptyIcon.create(16));
  }

  @Nullable
  public Color getTextColor() {
    switch (this) {
      case COPY_TO:
      case COPY_FROM:
        return FileStatus.ADDED.getColor();
      case MERGE:
        return FileStatus.MODIFIED.getColor();
      case DELETE:
        return FileStatus.DELETED.getColor();
      case EQUAL:
      case NONE:
    }
    return JBColor.foreground();
  }
}
