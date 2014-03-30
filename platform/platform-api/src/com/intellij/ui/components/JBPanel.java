/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBPanel extends JPanel implements TypeSafeDataProvider {
  @Nullable
  private Icon myBackgroundImage;
  @Nullable
  private Icon myCenterImage;

  public JBPanel(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
  }

  public JBPanel(LayoutManager layout) {
    super(layout);
  }

  public JBPanel(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  public JBPanel() {
    super();
  }

  @Nullable
  public Icon getBackgroundImage() {
    return myBackgroundImage;
  }

  public void setBackgroundImage(@Nullable Icon backgroundImage) {
    myBackgroundImage = backgroundImage;
  }

  @Nullable
  public Icon getCenterImage() {
    return myCenterImage;
  }

  public void setCenterImage(@Nullable Icon centerImage) {
    myCenterImage = centerImage;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Icon image = getBackgroundImage();
    if (image != null) {
      final int w = image.getIconWidth();
      final int h = image.getIconHeight();
      int x = 0;
      int y = 0;
      while (w > 0 &&  x < getWidth()) {
        while (h > 0 && y < getHeight()) {
          image.paintIcon(this, g, x, y);
          y+=h;
        }
        y=0;
        x+=w;
      }
    } else {
      super.paintComponent(g);
    }

    paintCenterImage(g);
  }

  protected void paintCenterImage(Graphics g) {
    Icon centerImage = getCenterImage();
    if (centerImage != null) {
      IconUtil.paintInCenterOf(this, g, centerImage);
    }
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    // override this to provide additional context
  }
}
