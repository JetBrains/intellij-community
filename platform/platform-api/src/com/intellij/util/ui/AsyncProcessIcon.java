/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.ui;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class AsyncProcessIcon extends AnimatedIcon {
  public static final int COUNT = 8;
  public static final int CYCLE_LENGTH = 1040;

  private static final Icon[] SMALL_ICONS = {
    AllIcons.Process.Step_1,
    AllIcons.Process.Step_2,
    AllIcons.Process.Step_3,
    AllIcons.Process.Step_4,
    AllIcons.Process.Step_5,
    AllIcons.Process.Step_6,
    AllIcons.Process.Step_7,
    AllIcons.Process.Step_8
  };

  public AsyncProcessIcon(@NonNls String name) {
    this(name, SMALL_ICONS, AllIcons.Process.Step_passive);
  }

  public AsyncProcessIcon(@NonNls String name, Icon[] icons, Icon passive) {
    super(name, icons, passive, CYCLE_LENGTH);
  }

  @Deprecated
  public AsyncProcessIcon setUseMask(boolean useMask) {
    return this;
  }

  @Override
  protected void paintIcon(Graphics g, Icon icon, int x, int y) {
    super.paintIcon(g, icon, x, y);
  }

  public void updateLocation(final JComponent container) {
    final Rectangle newBounds = calculateBounds(container);
    if (!newBounds.equals(getBounds())) {
      setBounds(newBounds);
      // painting problems with scrollpane
      // repaint shouldn't be called from paint method
      SwingUtilities.invokeLater(() -> container.repaint());
    }
  }

  @NotNull
  protected Rectangle calculateBounds(@NotNull JComponent container) {
    Rectangle rec = container.getVisibleRect();
    Dimension iconSize = getPreferredSize();
    return new Rectangle(rec.x + rec.width - iconSize.width, rec.y, iconSize.width, iconSize.height);
  }

  public static class Big extends AsyncProcessIcon {
    private static final Icon[] BIG_ICONS = {
      AllIcons.Process.Big.Step_1,
      AllIcons.Process.Big.Step_2,
      AllIcons.Process.Big.Step_3,
      AllIcons.Process.Big.Step_4,
      AllIcons.Process.Big.Step_5,
      AllIcons.Process.Big.Step_6,
      AllIcons.Process.Big.Step_7,
      AllIcons.Process.Big.Step_8
    };

    public Big(@NonNls final String name) {
      super(name, BIG_ICONS, AllIcons.Process.Big.Step_passive);
    }
  }

  public boolean isDisposed() {
    return myAnimator.isDisposed();
  }
}
