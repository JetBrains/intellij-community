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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 31
 * @author 2003
 */
public class ActionsHeaderComponent extends JPanel/*ScalableIconComponent*/ {
  private static final int HEADER_Y_OFFSET = 4;
  private JLabel myHeaderLabel;

  public void addNotify() {
    super.addNotify();
    final Dimension preferredSize = new Dimension(0, 0);
    calcPreferredSize(this, preferredSize);
    preferredSize.height += HEADER_Y_OFFSET;
    this.setPreferredSize(preferredSize);
  }

  public void setHeaderTextColor(Color color) {
    myHeaderLabel.setForeground(color);
  }

  private void calcPreferredSize(Container container, Dimension dimension) {
    final Component[] components = container.getComponents();
    int maxWidth = 0;
    int maxHeight = 0;
    for (Component component : components) {
      if (component instanceof Container) {
        calcPreferredSize((Container)component, dimension);
        maxWidth = Math.max(maxWidth, dimension.width);
        maxHeight = Math.max(maxHeight, dimension.height);
      }
      else {
        final Dimension preferredSize = component.getPreferredSize();
        maxWidth = Math.max(maxWidth, preferredSize.width);
        maxHeight = Math.max(maxHeight, preferredSize.height);
      }
    }
    final Dimension preferredSize = container.getPreferredSize();
    dimension.width = Math.max(maxWidth, preferredSize.width);
    dimension.height = Math.max(maxHeight, preferredSize.height);
  }

  public final void setSelected(boolean isSelected) {
    setBackground(isSelected? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground());
    myHeaderLabel.setForeground(isSelected? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground());
    this.revalidate();
    this.repaint();
  }
}
