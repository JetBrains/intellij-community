// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.dnd;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public interface DnDAware {
  void processMouseEvent(final MouseEvent e);

  boolean isOverSelection(final Point point);

  void dropSelectionButUnderPoint(Point point);

  @NotNull
  JComponent getComponent();
}
