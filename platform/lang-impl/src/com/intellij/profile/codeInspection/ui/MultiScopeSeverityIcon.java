/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class MultiScopeSeverityIcon implements Icon {
  private final int mySize;
  private final List<Color> myColors;

  public MultiScopeSeverityIcon(final int size, final List<Color> colors) {
    mySize = size;
    myColors = colors;
  }

  @Override
  public void paintIcon(final Component c, final Graphics g, final int i, final int j) {
    final int iconWidth = getIconWidth();
    final int iconHeightCoordinate = j + getIconHeight();

    final int partWidth = iconWidth / myColors.size();

    for (int idx = 0; idx < myColors.size(); idx++) {
      final Color color = myColors.get(idx);
      g.setColor(color);
      final int x = i + partWidth * idx;
      g.fillRect(x, j, x + partWidth, iconHeightCoordinate);
    }
  }

  @Override
  public int getIconWidth() {
    return mySize;
  }

  @Override
  public int getIconHeight() {
    return mySize;
  }
}
