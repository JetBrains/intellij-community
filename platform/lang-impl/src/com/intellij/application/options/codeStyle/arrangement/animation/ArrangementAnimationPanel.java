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
package com.intellij.application.options.codeStyle.arrangement.animation;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
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
  @Nullable private BufferedImage myCurrentImage;
  @Nullable private Listener      myListener;

  private boolean myExpand;
  private boolean myHorizontal;

  public ArrangementAnimationPanel(@NotNull JComponent content) {
    super(new GridBagLayout());
    myContent = content;
    add(content, new GridBag().fillCell().weightx(1).weighty(1));
    setOpaque(true);
    setBackground(UIUtil.getListBackground());
    doLayout();
  }

  public void startAnimation(boolean expand, boolean horizontal) {
    Dimension bounds = myContent.getPreferredSize();
    myContent.setBounds(0, 0, bounds.width, bounds.height);
    myContent.validate();
    myHorizontal = horizontal;
    myExpand = expand;
    myImage = UIUtil.createImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
    assert myImage != null;
    final Graphics2D graphics = myImage.createGraphics();
    UISettings.setupAntialiasing(graphics);
    graphics.setClip(0, 0, bounds.width, bounds.height);
    graphics.setColor(UIUtil.getListBackground());
    graphics.fillRect(0, 0, bounds.width, bounds.height);
    myContent.paint(graphics);
    graphics.dispose();

    if (expand) {
      if (horizontal) {
        myCurrentImage = myImage.getSubimage(0, 0, ArrangementConstants.ANIMATION_ITERATION_PIXEL_STEP, myImage.getHeight());
      }
      else {
        myCurrentImage = myImage.getSubimage(0, 0, myImage.getWidth(), ArrangementConstants.ANIMATION_ITERATION_PIXEL_STEP);
      }
    }
    else {
      if (horizontal) {
        myCurrentImage = myImage.getSubimage(
          0, 0, myImage.getWidth() - ArrangementConstants.ANIMATION_ITERATION_PIXEL_STEP, myImage.getHeight()
        );
      }
      else {
        myCurrentImage = myImage.getSubimage(
          0, 0, myImage.getWidth(), myImage.getHeight() - ArrangementConstants.ANIMATION_ITERATION_PIXEL_STEP
        );
      }
    }
    invalidate();
  }

  /**
   * Asks current panel to switch to the next drawing iteration
   * 
   * @return    <code>true</code> if there are more iterations
   */
  public boolean nextIteration() {
    int widthToUse = getImageWidthToUse();
    int heightToUse = getImageHeightToUse();
    if (widthToUse <= 0 || heightToUse <= 0) {
      myImage = null;
      myCurrentImage = null;
      return false;
    }

    myCurrentImage = myImage.getSubimage(0, 0, widthToUse, heightToUse);
    
    invalidate();
    return true;
  }

  @Override
  public void paint(Graphics g) {
    if (myCurrentImage == null) {
      super.paint(g);
      return;
    }

    g.drawImage(myCurrentImage, 0, 0, myCurrentImage.getWidth(), myCurrentImage.getHeight(), null);
    if (myListener != null) {
      myListener.onPaint();
    }
  }

  @Override
  public Dimension getMinimumSize() {
    if (myCurrentImage == null) {
      return myContent.getMinimumSize();
    }
    return new Dimension(myCurrentImage.getWidth(), myCurrentImage.getHeight());
  }

  @Override
  public Dimension getMaximumSize() {
    if (myCurrentImage == null) {
      return myContent.getMaximumSize();
    }
    return new Dimension(myCurrentImage.getWidth(), myCurrentImage.getHeight());
  }

  @Override
  public Dimension getPreferredSize() {
    if (myCurrentImage == null) {
      return myContent.getPreferredSize();
    }
    return new Dimension(myCurrentImage.getWidth(), myCurrentImage.getHeight());
  }

  private int getImageWidthToUse() {
    assert myCurrentImage != null;
    if (!myHorizontal) {
      return myCurrentImage.getWidth();
    }
    
    int sign = myExpand ? 1 : -1;
    int result = myCurrentImage.getWidth() + sign * ArrangementConstants.ANIMATION_ITERATION_PIXEL_STEP;

    if (result <= 0 || result > myImage.getWidth()) {
      return -1;
    }
    return result;
  }

  private int getImageHeightToUse() {
    assert myCurrentImage != null;
    if (myHorizontal) {
      return myCurrentImage.getHeight();
    }

    int sign = myExpand ? 1 : -1;
    int result = myCurrentImage.getHeight() + sign * ArrangementConstants.ANIMATION_ITERATION_PIXEL_STEP;

    if (result <= 0 || result > myImage.getHeight()) {
      return -1;
    }
    return result;
  }
  
  public void setListener(@Nullable Listener listener) {
    myListener = listener;
  }

  @Override
  public String toString() {
    return "animation panel for " + myContent.toString();
  }

  public interface Listener {
    void onPaint();
  }
}
