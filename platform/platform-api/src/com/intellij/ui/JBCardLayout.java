// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimerUtil;
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

public class JBCardLayout extends CardLayout {
  public enum SwipeDirection {FORWARD, BACKWARD, AUTO}

  private final Map<String, Component> myMap = new LinkedHashMap<>();
  private static final int mySwipeTime = 200;//default value, provide setter if need
  private static final int mySwipeSteps = 20;//default value, provide setter if need
  private final Timer myTimer = TimerUtil.createNamedTimer("CardLayoutTimer", Math.max(1, mySwipeTime / mySwipeSteps));
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
    stopSwipeIfNeeded();
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

  public void swipe(final @NotNull Container parent, final @NotNull String name, @NotNull SwipeDirection direction) {
    swipe(parent, name, direction, null);
  }

  public void swipe(final @NotNull Container parent, final @NotNull String name, @NotNull SwipeDirection direction,
                    final @Nullable Runnable onDone) {
    stopSwipeIfNeeded();
    mySwipeFrom = findVisible(parent);
    mySwipeTo = myMap.get(name);
    if (mySwipeTo == null) return;
    Application app = ApplicationManager.getApplication();
    if (mySwipeFrom == null || mySwipeFrom == mySwipeTo || (app != null && app.isUnitTestMode())) {
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
        linearProgress[0] = MathUtil.clamp((float)timePassed / mySwipeTime, 0, 1);
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

  private void stopSwipeIfNeeded() {
    if (myTimer.isRunning()) {
      myTimer.stop();
      mySwipeFrom = null;
      if (mySwipeTo != null) {
        mySwipeTo.setVisible(false);
        mySwipeTo = null;
      }
    }
  }

  private static @Nullable Component findVisible(Container parent) {
    for (int i = 0; i < parent.getComponentCount(); i++) {
      Component component = parent.getComponent(i);
      if (component.isVisible()) return component;
    }
    return null;
  }

  @Override
  public void first(Container parent) {
    stopSwipeIfNeeded();
    super.first(parent);
  }

  @Override
  public void next(Container parent) {
    stopSwipeIfNeeded();
    super.next(parent);
  }

  @Override
  public void previous(Container parent) {
    stopSwipeIfNeeded();
    super.previous(parent);
  }

  @Override
  public void last(Container parent) {
    stopSwipeIfNeeded();
    super.last(parent);
  }

  @Override
  public void show(Container parent, String name) {
    stopSwipeIfNeeded();
    super.show(parent, name);
  }

  @SuppressWarnings("HardCodedStringLiteral")
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
