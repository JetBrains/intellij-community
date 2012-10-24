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
package com.intellij.ui.tabs.impl;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
interface JBEditorTabsPainter {
  void doPaintInactive(Graphics2D g2d,
                       Rectangle effectiveBounds,
                       int x,
                       int y,
                       int w,
                       int h,
                       Color tabColor,
                       int row,
                       int column,
                       boolean vertical);

  void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle);

  void paintSelectionAndBorder(Graphics2D g2d,
                               Rectangle rect,
                               JBTabsImpl.ShapeInfo selectedShape,
                               Insets insets,
                               Color tabColor,
                               boolean horizontalTabs);

  Color getBackgroundColor();
}
