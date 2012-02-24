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
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author pegov
 */
public abstract class MoreTabsIcon {
  final Icon myIcon = IconLoader.getIcon("/general/moreTabs.png");
  private boolean myPainted;

  public void paintIcon(final Component c, final Graphics g) {
    final Rectangle moreRect = getIconRec();

    if (moreRect == null) return;

    int iconY = getIconY(moreRect);
    int iconX = getIconX(moreRect);

    if (myPainted) {
      myIcon.paintIcon(c, g, iconX, iconY);
    }
  }
  
  protected int getIconX(final Rectangle iconRec) {
    return iconRec.x + iconRec.width / 2 - (myIcon.getIconWidth() + 2) / 2;
  }

  protected int getIconY(final Rectangle iconRec) {
    return iconRec.y + iconRec.height / 2 - myIcon.getIconHeight() / 2;
  }

  @Nullable
  protected abstract Rectangle getIconRec();

  public void setPainted(boolean painted) {
    myPainted = painted;
  }
}
