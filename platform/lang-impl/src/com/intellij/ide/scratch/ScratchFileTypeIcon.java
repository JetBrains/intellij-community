// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.IconWithOverlay;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * @author Konstantin Bulenkov
 */
public class ScratchFileTypeIcon extends IconWithOverlay {
  public ScratchFileTypeIcon(Icon fileTypeIcon) {
    super(fileTypeIcon, AllIcons.Actions.Scratch);
  }

  @Override
  public @Nullable Shape getOverlayShape(int x, int y) {
    if (ExperimentalUI.isNewUI()) {
      float scale = getScale();
      double r = scale * (3.5 + 1.0);
      Icon overlay = getIcon(1);
      assert overlay != null;
      double w = overlay.getIconWidth();
      return new Ellipse2D.Double(x + w - 2 * r + scale, y - scale, 2 * r, 2 * r);
    }
    return null;
  }
}
