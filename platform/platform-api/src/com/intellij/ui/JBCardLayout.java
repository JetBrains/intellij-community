/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: Vassiliy.Kudryashov
 */
public class JBCardLayout extends CardLayout {
  public enum SwipeDirection {FORWARD, BACKWARD, AUTO}

  private Map<String, Component> myMap = new LinkedHashMap<>();
  private int mySwipeTime = 200;//default value, provide setter if need
  private int mySwipeSteps = 20;//default value, provide setter if need
  private final Timer myTimer = UIUtil.createNamedTimer("CardLayoutTimer",Math.max(1, mySwipeTime / mySwipeSteps));
  private Component mySwipeFrom = null;
  private Component mySwipeTo = null;

  public JBCardLayout() {
    this(0, 0);
  }

  public JBCardLayout(int hgap, int vgap) {
    super(hgap, vgap);
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    super.addLayoutComponent(name, comp);
    myMap.put(name, comp);
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    stopSwipeIfNeed();
    super.removeLayoutComponent(comp);
    for (Iterator<Map.Entry<String, Component>> iterator = myMap.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, Component> entry = iterator.next();
      if (entry.getValue() == comp) {
        iterator.remove();
        break;
      }
    }
  }

  @Override
  public void layoutContainer(Container parent) {
    if (!myTimer.isRunning()) {
      super.layoutContainer(parent);
    }
  }

  public void swipe(@NotNull final Container parent, @NotNull final String name, @NotNull SwipeDirection direction) {
    swipe(parent, name, direction, null);
  }

  public void swipe(@NotNull final Container parent, @NotNull final String name, @NotNull SwipeDirection direction,
                    final @Nullable Runnable onDone) {
    stopSwipeIfNeed();
    mySwipeFrom = findVisible(parent);
    mySwipeTo = myMap.get(name);
    if (mySwipeTo == null) return;
    if (mySwipeFrom == null || mySwipeFrom == mySwipeTo) {
      super.show(parent, name);
      if (onDone != null) {
        onDone.run();
      }
      return;
    }
    final boolean isForward;
    if (direction == SwipeDirection.AUTO) {
      boolean b = true;
      for (Component component : myMap.values()) {
        if (component == mySwipeFrom || component == mySwipeTo) {
          b = component == mySwipeFrom;
          break;
        }
      }
      isForward = b;
    }
    else {
      isForward = direction == SwipeDirection.FORWARD;
    }
    final double[] linearProgress = new double[1];
    linearProgress[0] = 0;
    final long startTime = System.currentTimeMillis();
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        long timePassed = System.currentTimeMillis() - startTime;
        if (timePassed >= mySwipeTime) {
          Component currentFocusComponent = IdeFocusManager.getGlobalInstance().getFocusedDescendantFor(parent);
          show(parent, name);
          if (currentFocusComponent != null) currentFocusComponent.requestFocusInWindow();
          if (onDone != null) {
            onDone.run();
          }
          return;
        }
        linearProgress[0] = Math.min(1, Math.max(0, (float)timePassed / mySwipeTime));
        double naturalProgress = (1 - Math.cos(Math.PI * linearProgress[0])) / 2;
        Rectangle bounds = new Rectangle(parent.getWidth(), parent.getHeight());
        JBInsets.removeFrom(bounds, parent.getInsets());
        Rectangle r = new Rectangle(bounds);
        int x = (int)((naturalProgress * r.width));
        r.translate(isForward ? -x : x, 0);
        mySwipeFrom.setBounds(r);
        Rectangle r2 = new Rectangle(bounds);
        r2.translate(isForward ? r2.width - x : x - r2.width, 0);
        mySwipeTo.setVisible(true);
        mySwipeTo.setBounds(r2);
        parent.repaint();
      }
    };
    for (ActionListener actionListener : myTimer.getActionListeners()) {
      myTimer.removeActionListener(actionListener);
    }
    myTimer.addActionListener(listener);
    myTimer.start();
  }

  private void stopSwipeIfNeed() {
    if (myTimer.isRunning()) {
      myTimer.stop();
      mySwipeFrom = null;
      if (mySwipeTo != null) {
        mySwipeTo.setVisible(false);
        mySwipeTo = null;
      }
    }
  }

  @Nullable
  private static Component findVisible(Container parent) {
    for (int i = 0; i < parent.getComponentCount(); i++) {
      Component component = parent.getComponent(i);
      if (component.isVisible()) return component;
    }
    return null;
  }

  @Override
  public void first(Container parent) {
    stopSwipeIfNeed();
    super.first(parent);
  }

  @Override
  public void next(Container parent) {
    stopSwipeIfNeed();
    super.next(parent);
  }

  @Override
  public void previous(Container parent) {
    stopSwipeIfNeed();
    super.previous(parent);
  }

  @Override
  public void last(Container parent) {
    stopSwipeIfNeed();
    super.last(parent);
  }

  @Override
  public void show(Container parent, String name) {
    stopSwipeIfNeed();
    super.show(parent, name);
  }

  public static void main(String[] args) throws IOException {
    final JBCardLayout cardLayout = new JBCardLayout();

    JFrame f = new JFrame();
    JPanel p = new JPanel(new BorderLayout());
    final JPanel centerPanel = new JPanel(cardLayout);
    for (int i = 0; i < 10; i++) {
      JLabel l = new JLabel("Page #" + (i + 1), SwingConstants.CENTER);
      l.setOpaque(true);
      int red = 50 + (int)(Math.random() * 100);
      int green = 50 + (int)(Math.random() * 100);
      int blue = 50 + (int)(Math.random() * 100);
      l.setForeground(new Color(red, green, blue));
      red = 255 - (int)(Math.random() * 55);
      green = 255 - (int)(Math.random() * 55);
      blue = 255 - (int)(Math.random() * 55);
      l.setBackground(new Color(red, green, blue));
      l.setFont(l.getFont().deriveFont(40F));
      centerPanel.add(l, "page" + i);
    }
    final int[] cursor = new int[1];
    JButton prevButton = new JButton("<<");
    JButton nextButton = new JButton(">>");
    prevButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cursor[0]--;
        if (cursor[0] < 0) {
          cursor[0] = 9;
        }
        cardLayout.swipe(centerPanel, "page" + cursor[0], SwipeDirection.BACKWARD);
      }
    });
    nextButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cursor[0] = (cursor[0] + 1) % 10;
        cardLayout.swipe(centerPanel, "page" + cursor[0], SwipeDirection.FORWARD);
      }
    });

    p.setLayout(new BorderLayout());
    p.add(prevButton, BorderLayout.WEST);
    p.add(centerPanel, BorderLayout.CENTER);
    p.add(nextButton, BorderLayout.EAST);
    f.setContentPane(p);
    f.setSize(JBUI.size(600, 800));
    f.setLocationRelativeTo(null);
    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    f.setVisible(true);
  }

  public Component findComponentById(String id) {
    return myMap.get(id);
  }
}
