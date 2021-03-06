// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class BaselinePanel extends NonOpaquePanel {
  private Component myBaseComponent;
  private final List<Component> myButtonComponents = new ArrayList<>();
  private boolean[] myButtonEnableStates;
  private Component myProgressComponent;
  private int myYOffset;

  private final JBValue myOffset = new JBValue.Float(8);
  private final JBValue myBeforeButtonOffset = new JBValue.Float(40);
  private final JBValue myButtonOffset = new JBValue.Float(6);

  private EventHandler myEventHandler;

  public BaselinePanel() {
    setBorder(JBUI.Borders.empty(5, 0, 6, 0));

    setLayout(new AbstractLayoutManager() {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Dimension baseSize = myBaseComponent.getPreferredSize();
        int width = baseSize.width;

        if (myProgressComponent == null) {
          int size = myButtonComponents.size();
          if (size > 0) {
            int visibleCount = 0;

            for (Component component : myButtonComponents) {
              if (component.isVisible()) {
                width += component.getPreferredSize().width;
                visibleCount++;
              }
            }

            if (visibleCount > 0) {
              width += myBeforeButtonOffset.get();
              width += (visibleCount - 1) * myButtonOffset.get();
            }
          }
        }
        else {
          width += myOffset.get() + myProgressComponent.getPreferredSize().width;
        }

        Insets insets = parent.getInsets();
        return new Dimension(width, insets.top + baseSize.height + insets.bottom);
      }

      private int calculateBaseWidth(@NotNull Container parent) {
        int parentWidth = parent.getWidth();

        if (myProgressComponent != null) {
          return parentWidth - myProgressComponent.getPreferredSize().width - myOffset.get();
        }

        int visibleCount = 0;
        for (Component component : myButtonComponents) {
          if (component.isVisible()) {
            parentWidth -= component.getPreferredSize().width;
            visibleCount++;
          }
        }
        parentWidth -= myButtonOffset.get() * (visibleCount - 1);
        if (visibleCount > 0) {
          parentWidth -= myOffset.get();
        }

        return parentWidth;
      }

      @Override
      public void layoutContainer(Container parent) {
        Dimension baseSize = myBaseComponent.getPreferredSize();
        int top = parent.getInsets().top;
        int y = top + myBaseComponent.getBaseline(baseSize.width, baseSize.height);
        int x = 0;
        int calcBaseWidth = calculateBaseWidth(parent);

        if (myBaseComponent instanceof JLabel) {
          JLabel label = (JLabel)myBaseComponent;
          label.setToolTipText(calcBaseWidth < baseSize.width ? label.getText() : null);
        }

        baseSize.width = Math.min(baseSize.width, calcBaseWidth);
        myBaseComponent.setBounds(x, top, baseSize.width, baseSize.height);

        if (myProgressComponent != null) {
          Dimension size = myProgressComponent.getPreferredSize();
          setBaselineBounds(parent.getWidth() - size.width, y, myProgressComponent, size);
          return;
        }

        int lastX = parent.getWidth();

        for (int i = myButtonComponents.size() - 1; i >= 0; i--) {
          Component component = myButtonComponents.get(i);
          if (!component.isVisible()) {
            continue;
          }
          Dimension size = component.getPreferredSize();
          lastX -= size.width;
          setBaselineBounds(lastX, y - myYOffset, component, size);
          lastX -= myButtonOffset.get();
        }
      }

      private void setBaselineBounds(int x, int y, @NotNull Component component, @NotNull Dimension size) {
        setBaselineBounds(x, y, component, size, size.width, size.height);
      }

      private void setBaselineBounds(int x, int y, @NotNull Component component, @NotNull Dimension prefSize, int width, int height) {
        if (component instanceof ActionToolbar) {
          component.setBounds(x, getInsets().top - JBUI.scale(1), width, height);
        }
        else {
          component.setBounds(x, y - component.getBaseline(prefSize.width, prefSize.height), width, height);
        }
      }
    });
  }

  public void setListeners(@NotNull EventHandler eventHandler) {
    myEventHandler = eventHandler;
  }

  public void setYOffset(int YOffset) {
    myYOffset = YOffset;
  }

  @Override
  public Component add(Component component) {
    assert myBaseComponent == null;
    myBaseComponent = component;
    return super.add(component);
  }

  @NotNull
  public List<Component> getButtonComponents() {
    return myButtonComponents;
  }

  public void addButtonComponent(@NotNull JComponent component) {
    myButtonComponents.add(component);
    add(component, null);
  }

  public void removeButtons() {
    List<Component> buttons = new ArrayList<>(myButtonComponents);
    myButtonComponents.clear();
    myButtonEnableStates = null;

    for (Component button : buttons) {
      remove(button);
    }
  }

  public void setProgressComponent(@Nullable ListPluginComponent pluginComponent, @NotNull JComponent progressComponent) {
    assert myProgressComponent == null;
    myProgressComponent = progressComponent;
    add(progressComponent, null);

    if (myEventHandler != null && pluginComponent != null) {
      myEventHandler.addAll(progressComponent);
      myEventHandler.updateHover(pluginComponent);
    }

    setVisibleOther(false);
    doLayout();
  }

  public void removeProgressComponent() {
    if (myProgressComponent == null) {
      return;
    }

    remove(myProgressComponent);
    myProgressComponent = null;

    setVisibleOther(true);
    doLayout();
  }

  private void setVisibleOther(boolean value) {
    if (myButtonComponents.isEmpty()) {
      return;
    }

    if (value) {
      assert myButtonEnableStates != null && myButtonEnableStates.length == myButtonComponents.size();

      for (int i = 0, size = myButtonComponents.size(); i < size; i++) {
        myButtonComponents.get(i).setVisible(myButtonEnableStates[i]);
      }
      myButtonEnableStates = null;
    }
    else {
      assert myButtonEnableStates == null;
      myButtonEnableStates = new boolean[myButtonComponents.size()];

      for (int i = 0, size = myButtonComponents.size(); i < size; i++) {
        Component component = myButtonComponents.get(i);
        myButtonEnableStates[i] = component.isVisible();
        component.setVisible(false);
      }
    }
  }
}