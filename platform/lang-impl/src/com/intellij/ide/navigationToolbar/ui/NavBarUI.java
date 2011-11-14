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
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.ide.navigationToolbar.NavBarItem;
import com.intellij.ide.navigationToolbar.NavBarPanel;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public interface NavBarUI {
  Insets getElementIpad(boolean isPopupElement);
  Insets getElementPadding();
  Font getElementFont(NavBarItem navBarItem);

  short getSelectionAlpha();

  boolean isDrawMacShadow(boolean selected, boolean focused);

  void doPaintNavBarItem(Graphics2D g, NavBarItem item, NavBarPanel navbar);
  
  Dimension getOffsets(NavBarItem item);
  
  Color getBackground(boolean selected, boolean focused);
  @Nullable
  Color getForeground(boolean selected, boolean focused, boolean inactive);
  
  void doPaintWrapperPanel(Graphics2D g, Rectangle bounds, boolean mainToolbarVisible);
  void doPaintWrapperPanelChildren(Graphics2D g, Rectangle bounds, boolean mainToolbarVisible);

  void doPaintNavBarPanel(Graphics2D g, Rectangle bounds, boolean mainToolbarVisible, boolean undocked);

  Insets getWrapperPanelInsets(Insets insets);
}
