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

package com.intellij.util.ui;

import com.intellij.ui.ClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public abstract class StatusText {
  public static final SimpleTextAttributes DEFAULT_ATTRIBUTES = SimpleTextAttributes.GRAYED_ATTRIBUTES;
  public static final String DEFAULT_EMPTY_TEXT = UIBundle.message("message.nothingToShow");

  @Nullable
  private Component myOwner;
  private Component myMouseTarget;
  private final MouseMotionListener myMouseMotionListener;
  private final ClickListener myClickListener;

  private boolean myIsDefaultText;

  private String myText = "";
  protected final SimpleColoredComponent myComponent = new SimpleColoredComponent();
  private final ArrayList<ActionListener> myClickListeners = new ArrayList<ActionListener>();

  protected StatusText(JComponent owner) {
    this();
    attachTo(owner);
  }

  public StatusText() {
    myClickListener = new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        if (e.getButton() == MouseEvent.BUTTON1 && clickCount == 1) {
          ActionListener actionListener = findActionListenerAt(e.getPoint());
          if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(this, 0, ""));
            return true;
          }
        }
        return false;
      }
    };

    myMouseMotionListener = new MouseAdapter() {
      @Override
      public void mouseMoved(final MouseEvent e) {
        if (isStatusVisible()) {
          if (findActionListenerAt(e.getPoint()) != null) {
            myMouseTarget.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          }
          else {
            myMouseTarget.setCursor(Cursor.getDefaultCursor());
          }
        }
      }
    };

    myComponent.setOpaque(false);
    myComponent.setFont(UIUtil.getLabelFont());
    setText(DEFAULT_EMPTY_TEXT, DEFAULT_ATTRIBUTES);
    myIsDefaultText = true;
  }

  public void attachTo(@Nullable Component owner) {
    attachTo(owner, owner);
  }

  public void attachTo(@Nullable Component owner, @Nullable Component mouseTarget) {
    if (myMouseTarget != null) {
      myClickListener.uninstall(myMouseTarget);
      myMouseTarget.removeMouseMotionListener(myMouseMotionListener);
    }

    myOwner = owner;
    myMouseTarget = mouseTarget;

    if (myMouseTarget != null) {
      myClickListener.installOn(myMouseTarget);
      myMouseTarget.addMouseMotionListener(myMouseMotionListener);
    }
  }

  protected abstract boolean isStatusVisible();

  @Nullable
  private ActionListener findActionListenerAt(Point point) {
    if (!isStatusVisible()) return null;

    point = SwingUtilities.convertPoint(myMouseTarget, point, myOwner);

    Rectangle b = getTextComponentBound();
    if (b.contains(point)) {
      int index = myComponent.findFragmentAt(point.x - b.x);
      if (index >= 0 && index < myClickListeners.size()) {
        return myClickListeners.get(index);
      }
    }
    return null;
  }

  protected Rectangle getTextComponentBound() {
    Rectangle ownerRec = myOwner == null ? new Rectangle(0, 0, 0, 0) : myOwner.getBounds();

    Dimension size = myComponent.getPreferredSize();
    int x = (ownerRec.width - size.width) / 2;
    int y = (ownerRec.height - size.height) / 3;
    return new Rectangle(x, y, size.width, size.height);
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public StatusText setText(String text) {
    return setText(text, DEFAULT_ATTRIBUTES);
  }

  public StatusText setText(String text, SimpleTextAttributes attrs) {
    return clear().appendText(text, attrs);
  }

  public StatusText clear() {
    myText = "";
    myComponent.clear();
    myClickListeners.clear();
    if (myOwner != null) myOwner.repaint();
    return this;
  }

  public StatusText appendText(String text) {
    return appendText(text, DEFAULT_ATTRIBUTES);
  }

  public StatusText appendText(String text, SimpleTextAttributes attrs) {
    return appendText(text, attrs, null);
  }

  public StatusText appendText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    if (myIsDefaultText) {
      clear();
      myIsDefaultText = false;
    }

    myText += text;
    myComponent.append(text, attrs);
    myClickListeners.add(listener);
    if (myOwner != null) myOwner.repaint();
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

  @NotNull
  public SimpleColoredComponent getComponent() {
    return myComponent;
  }

  public Dimension getPreferredSize() {
    return myComponent.getPreferredSize();
  }
}
