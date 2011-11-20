/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.openapi.util.IconLoader;

import java.awt.*;

/**
 * @author pegov
 */
public abstract class MoreTabsIcon extends MoreIcon {
  
  private final ActiveIcon myIcon =
    new ActiveIcon(IconLoader.getIcon("/general/moreTabs.png"), IconLoader.getIcon("/general/moreTabs.png"));

  @Override
  public void paintIcon(final Component c, final Graphics g) {
    myIcon.setActive(isActive());

    final Rectangle moreRect = getIconRec();

    if (moreRect == null) return;

    int iconY = getIconY(moreRect);
    int iconX = getIconX(moreRect);

    if (myLeftPainted || myRightPainted) {
      myIcon.paintIcon(c, g, iconX, iconY);
    }
  }
  
  @Override
  public int getIconWidth() {
    return myIcon.getIconWidth() + myGap; 
  }

  @Override
  public int getIconHeight() {
    return myIcon.getIconHeight();
  }
}
