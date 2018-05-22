/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
* @author nik
*/
class TextComponentEmptyText extends StatusText {
  private final JTextComponent myOwner;
  private String myStatusTriggerText = "";

  public TextComponentEmptyText(JTextComponent owner) {
    super(owner);
    myOwner = owner;
    clear();
    myOwner.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myOwner.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        myOwner.repaint();
      }
    });
  }

  public void setTextToTriggerStatus(@NotNull String defaultText) {
    myStatusTriggerText = defaultText;
  }

  @NotNull
  public String getStatusTriggerText() {
    return myStatusTriggerText;
  }

  public void paintStatusText(Graphics g) {
    getComponent().setFont(myOwner.getFont());
    if (!isVerticalFlow()) {
      getSecondaryComponent().setFont(myOwner.getFont());
    }
    paint(myOwner, g);
  }

  @Override
  protected boolean isStatusVisible() {
    return myOwner.getText().equals(myStatusTriggerText) && !myOwner.isFocusOwner();
  }

  @Override
  protected Rectangle getTextComponentBound() {
    Rectangle b = myOwner.getBounds();
    Insets insets = ObjectUtils.notNull(myOwner.getInsets(), JBUI.emptyInsets());
    Insets margin = ObjectUtils.notNull(myOwner.getMargin(), JBUI.emptyInsets());
    Insets ipad = getComponent().getIpad();
    int left = insets.left + margin.left - ipad.left;
    int right = insets.right + margin.right - ipad.right;
    int top = insets.top + margin.top - ipad.top;
    int bottom = insets.bottom + margin.bottom - ipad.bottom;
    return new Rectangle(left, top,
                         b.width - left - right,
                         b.height - top - bottom);
  }
}
