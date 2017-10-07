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
  private JTextComponent myOwner;
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
    paint(myOwner, g);
  }

  @Override
  protected boolean isStatusVisible() {
    return myOwner.getText().equals(myStatusTriggerText) && !myOwner.isFocusOwner();
  }

  @Override
  protected Rectangle getTextComponentBound() {
    Rectangle b = myOwner.getBounds();
    Insets insets = myOwner.getInsets();
    int left = insets.left >> 1;
    int right = insets.right >> 1;
    return new Rectangle(left, insets.top, 
                         b.width - left - right, 
                         b.height - insets.top - insets.bottom);
  }
}
