/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public abstract class EmptyTextHelper implements ComponentWithEmptyText{
  public static final SimpleTextAttributes DEFAULT_ATTRIBUTES = SimpleTextAttributes.GRAYED_ATTRIBUTES;

  private final JComponent myOwner;

  private String myEmptyText = "";
  private final SimpleColoredComponent myEmptyTextComponent = new SimpleColoredComponent();
  private final ArrayList<ActionListener> myEmptyTextClickListeners = new ArrayList<ActionListener>();

  public EmptyTextHelper(JComponent owner) {
    myOwner = owner;
    
    myOwner.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getButton() == 1 && e.getClickCount() == 1 && isEmpty()) {
          ActionListener actionListener = findEmptyTextActionListenerAt(e.getPoint());
          if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(this, 0, ""));
          }
        }
      }
    });
    myOwner.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(final MouseEvent e) {
        if (isEmpty()) {
          if (findEmptyTextActionListenerAt(e.getPoint()) != null) {
            myOwner.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          }
          else {
            myOwner.setCursor(Cursor.getDefaultCursor());
          }
        }
      }
    });
    myEmptyTextComponent.setFont(UIUtil.getLabelFont());

    setEmptyText(UIBundle.message("message.nothingToShow"), DEFAULT_ATTRIBUTES);
  }

  @Nullable
  private ActionListener findEmptyTextActionListenerAt(final Point point) {
    Rectangle b = getTextComponentBound();
    if (b.contains(point)) {
      int index = myEmptyTextComponent.findFragmentAt(point.x - b.x);
      if (index >= 0 && index < myEmptyTextClickListeners.size()) {
        return myEmptyTextClickListeners.get(index);
      }
    }
    return null;
  }

  private Rectangle getTextComponentBound() {
    Rectangle bounds = myOwner.getBounds();
    Dimension size = myEmptyTextComponent.getPreferredSize();
    int x = (bounds.width - size.width) / 2;
    int y = bounds.height / 3;
    return new Rectangle(x, y, size.width, size.height);
  }

  public String getEmptyText() {
    return myEmptyText;
  }

  public void setEmptyText(String emptyText) {
    setEmptyText(emptyText, DEFAULT_ATTRIBUTES);
  }

  public void setEmptyText(String emptyText, SimpleTextAttributes attrs) {
    clearEmptyText();
    appendEmptyText(emptyText, attrs);
  }

  public void clearEmptyText() {
    myEmptyTextComponent.clear();
    myEmptyTextClickListeners.clear();
    myEmptyText = "";
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs) {
    appendEmptyText(text, attrs, null);
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    myEmptyText += text;
    myEmptyTextComponent.append(text, attrs);
    myEmptyTextClickListeners.add(listener);
  }

  public void paint(Graphics g) {
    if (isEmpty() && myEmptyText.length() > 0) {
      myEmptyTextComponent.setFont(myOwner.getFont());
      myEmptyTextComponent.setBackground(myOwner.getBackground());
      myEmptyTextComponent.setForeground(myOwner.getForeground());

      Rectangle b = getTextComponentBound();
      myEmptyTextComponent.setBounds(0, 0, b.width, b.height);

      Graphics g2 = g.create(b.x, b.y, b.width, b.height);
      myEmptyTextComponent.paint(g2);
    }
  }

  protected abstract boolean isEmpty();
}