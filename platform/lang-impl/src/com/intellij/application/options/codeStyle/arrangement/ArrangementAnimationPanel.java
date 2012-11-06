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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Denis Zhdanov
 * @since 11/2/12 8:50 PM
 */
public class ArrangementAnimationPanel extends JPanel {

  @NotNull private final JComponent myContent;

  @Nullable private BufferedImage myImage;
  @Nullable private Listener myListener;

  private int myAnimationSteps = -1;
  
  private boolean myExpand;
  private boolean myHorizontal;

  public ArrangementAnimationPanel(@NotNull JComponent content) {
    super(new GridBagLayout());
    myContent = content;
    add(content, new GridBag().fillCell().weightx(1).weighty(1));
  }

  public void startAnimation(boolean expand, boolean horizontal) {
    Rectangle bounds = getBounds();
    if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
      return;
    }
    myHorizontal = horizontal;
    myAnimationSteps = ArrangementConstants.ANIMATION_STEPS - 1;
    myExpand = expand;
    myImage = UIUtil.createImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
    assert myImage != null;
    final Graphics2D graphics = myImage.createGraphics();
    UISettings.setupAntialiasing(graphics);
    graphics.translate(-bounds.x, -bounds.y);
    graphics.setClip(bounds.x, bounds.y, bounds.width, bounds.height);
    super.paint(graphics);
    graphics.dispose();
    invalidate();
  }

  @Override
  public void paint(Graphics g) {
    if (myImage == null) {
      super.paint(g);
      return;
    }
    g.drawImage(myImage, 0, 0, getImageWidthToUse(), getImageHeightToUse(), null);
    myAnimationSteps--;
    if (myAnimationSteps <= 0) {
      myImage = null;
      if (myListener != null) {
        myListener.onFinished();
      }
    }
    else if (myListener != null) {
      myListener.onNewIteration();
    }
  }

  @Override
  public Dimension getMinimumSize() {
    if (myImage == null) {
      return myContent.getMinimumSize();
    }
    return new Dimension(getImageWidthToUse(), getImageHeightToUse());
  }

  @Override
  public Dimension getMaximumSize() {
    if (myImage == null) {
      return myContent.getMaximumSize();
    }
    return new Dimension(getImageWidthToUse(), getImageHeightToUse());
  }

  @Override
  public Dimension getPreferredSize() {
    if (myImage == null) {
      return myContent.getPreferredSize();
    }
    return new Dimension(getImageWidthToUse(), getImageHeightToUse());
  }

  private int getImageWidthToUse() {
    assert myImage != null;
    if (myHorizontal) {
      return myImage.getWidth() * getUnits() / ArrangementConstants.ANIMATION_STEPS;
    }
    else {
      return myImage.getWidth();
    }
  }

  private int getImageHeightToUse() {
    assert myImage != null;
    if (myHorizontal) {
      return myImage.getHeight();
    }
    else {
      return myImage.getHeight() * getUnits() / ArrangementConstants.ANIMATION_STEPS;
    }
  }
  
  private int getUnits() {
    int units;
    if (myExpand) {
      units = ArrangementConstants.ANIMATION_STEPS - myAnimationSteps;
    }
    else {
      units = myAnimationSteps;
    }
    return units;
  }

  public void setListener(@Nullable Listener listener) {
    myListener = listener;
  }

  @Override
  public String toString() {
    return "animation panel for " + myContent.toString();
  }

  public interface Listener {
    void onNewIteration();
    void onFinished();
  }
}
