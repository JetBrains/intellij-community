/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class RefreshFileSystemIcon extends AnimatedIcon {
  private static final Icon[] ICONS = getIcons("/process/fs/step_", 18);

  private static Icon[] getIcons(String path, int count) {
    Icon[] icons = new Icon[count];
    for (int i = 0; i < icons.length; i++) {
      int index = i + 1;
      icons[i] = IconLoader.getIcon(path + index + ".png");
    }
    return icons;
  }

  public RefreshFileSystemIcon() {
    super("Refreshing filesystem", ICONS, EmptyIcon.ICON_16, 800);
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isRunning()) return new Dimension(0, 0);
    return super.getPreferredSize();
  }

  @Override
  public void paint(Graphics g) {
    g.translate(0, -1);
    super.paint(g);
    g.translate(0, 1);
  }
}
