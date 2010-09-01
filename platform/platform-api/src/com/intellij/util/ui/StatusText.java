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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public abstract class StatusText implements ComponentWithEmptyText {
  public static final SimpleTextAttributes DEFAULT_ATTRIBUTES = SimpleTextAttributes.GRAYED_ATTRIBUTES;

  @Nullable
  private Component myOwner;
  private final MouseAdapter myMouseListener;

  private String myText = "";
  private final SimpleColoredComponent myComponent = new SimpleColoredComponent();
  private final ArrayList<ActionListener> myClickListeners = new ArrayList<ActionListener>();

  protected StatusText(JComponent owner) {
    this();
    attachTo(owner);
  }

  public StatusText() {
    myMouseListener = new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getButton() == 1 && e.getClickCount() == 1) {
          ActionListener actionListener = findEmptyTextActionListenerAt(e.getPoint());
          if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(this, 0, ""));
          }
        }
      }

      @Override
      public void mouseMoved(final MouseEvent e) {
        if (findEmptyTextActionListenerAt(e.getPoint()) != null) {
          myOwner.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        else {
          myOwner.setCursor(Cursor.getDefaultCursor());
        }
      }
    };

    myComponent.setOpaque(false);
    myComponent.setFont(UIUtil.getLabelFont());
    setEmptyText(UIBundle.message("message.nothingToShow"), DEFAULT_ATTRIBUTES);
  }

  public void attachTo(@Nullable Component owner) {
    if (myOwner != null) {
      myOwner.removeMouseListener(myMouseListener);
      myOwner.removeMouseMotionListener(myMouseListener);
    }

    myOwner = owner;

    if (myOwner != null) {
      myOwner.addMouseListener(myMouseListener);
      myOwner.addMouseMotionListener(myMouseListener);
    }
  }

  protected abstract boolean isStatusVisible();

  @Nullable
  private ActionListener findEmptyTextActionListenerAt(final Point point) {
    if (!isStatusVisible()) return null;

    Rectangle b = getTextComponentBound();
    if (b.contains(point)) {
      int index = myComponent.findFragmentAt(point.x - b.x);
      if (index >= 0 && index < myClickListeners.size()) {
        return myClickListeners.get(index);
      }
    }
    return null;
  }

  private Rectangle getTextComponentBound() {
    Rectangle bounds = myOwner == null ? new Rectangle(0, 0, 0, 0) : myOwner.getBounds();

    Dimension size = myComponent.getPreferredSize();
    int x = (bounds.width - size.width) / 2;
    int y = bounds.height / 3;
    return new Rectangle(x, y, size.width, size.height);
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public void setEmptyText(String emptyText) {
    setEmptyText(emptyText, DEFAULT_ATTRIBUTES);
  }

  public void setEmptyText(String emptyText, SimpleTextAttributes attrs) {
    clearEmptyText();
    appendEmptyText(emptyText, attrs);
  }

  public void clearEmptyText() {
    myText = "";
    myComponent.clear();
    myClickListeners.clear();
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs) {
    appendEmptyText(text, attrs, null);
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    myText += text;
    myComponent.append(text, attrs);
    myClickListeners.add(listener);
  }

  @Override
  public StatusText getEmptyText() {
    return this;
  }

  public void paint(Component owner, Graphics g) {
    if (!isStatusVisible() || owner != myOwner) return;

    Rectangle b = getTextComponentBound();
    myComponent.setBounds(0, 0, b.width, b.height);

    Graphics2D g2 = (Graphics2D)g.create(b.x, b.y, b.width, b.height);
    myComponent.paint(g2);
    g2.dispose();
  }
}