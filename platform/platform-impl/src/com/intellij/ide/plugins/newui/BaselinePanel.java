// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class BaselinePanel extends NonOpaquePanel {
  private Component myBaseComponent;
  private final java.util.List<Component> myVersionComponents = new ArrayList<>();
  private final List<Component> myButtonComponents = new ArrayList<>();
  private Component myProgressComponent;

  private final JBValue myOffset = new JBValue.Float(8);
  private final JBValue myBeforeButtonOffset = new JBValue.Float(40);
  private final JBValue myButtonOffset = new JBValue.Float(6);

  private JLabel myErrorComponent;
  private Component myErrorEnableComponent;

  private EventHandler myEventHandler;

  public BaselinePanel() {
    setBorder(JBUI.Borders.empty(5, 0, 6, 0));

    setLayout(new AbstractLayoutManager() {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Dimension baseSize = myBaseComponent.getPreferredSize();
        int width = baseSize.width;

        if (myProgressComponent == null) {
          for (Component component : myVersionComponents) {
            if (!component.isVisible()) {
              break;
            }
            width += myOffset.get() + component.getPreferredSize().width;
          }

          if (myErrorComponent != null) {
            width += myOffset.get() + myErrorComponent.getPreferredSize().width;

            if (myErrorEnableComponent != null) {
              width += myOffset.get() + myErrorEnableComponent.getPreferredSize().width;
            }
          }

          int size = myButtonComponents.size();
          if (size > 0) {
            width += myBeforeButtonOffset.get();
            width += (size - 1) * myButtonOffset.get();

            for (Component component : myButtonComponents) {
              width += component.getPreferredSize().width;
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

        if (!myVersionComponents.isEmpty() && myVersionComponents.get(0).isVisible()) {
          for (Component component : myVersionComponents) {
            parentWidth -= component.getPreferredSize().width;
          }
          parentWidth -= myOffset.get() * myVersionComponents.size();
        }

        for (Component component : myButtonComponents) {
          parentWidth -= component.getPreferredSize().width;
        }
        parentWidth -= myButtonOffset.get() * (myButtonComponents.size() - 1);

        if (myErrorComponent != null) {
          if (myErrorEnableComponent != null) {
            parentWidth -= (myOffset.get() + myErrorEnableComponent.getPreferredSize().width);
          }

          int errorPartWidth = myErrorComponent.getPreferredSize().width / 3;
          if (myBaseComponent.getPreferredSize().width >= (parentWidth - errorPartWidth)) {
            parentWidth -= errorPartWidth;
          }
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

        JLabel label = (JLabel)myBaseComponent;
        label.setToolTipText(calcBaseWidth < baseSize.width ? label.getText() : null);

        baseSize.width = Math.min(baseSize.width, calcBaseWidth);
        myBaseComponent.setBounds(x, top, baseSize.width, baseSize.height);
        x += baseSize.width;

        if (myProgressComponent != null) {
          Dimension size = myProgressComponent.getPreferredSize();
          setBaselineBounds(parent.getWidth() - size.width, y, myProgressComponent, size);
          return;
        }

        for (Component component : myVersionComponents) {
          if (!component.isVisible()) {
            break;
          }
          Dimension size = component.getPreferredSize();
          x += myOffset.get();
          setBaselineBounds(x, y, component, size);
          x += size.width;
        }

        int lastX = parent.getWidth();

        for (int i = myButtonComponents.size() - 1; i >= 0; i--) {
          Component component = myButtonComponents.get(i);
          Dimension size = component.getPreferredSize();
          lastX -= size.width;
          setBaselineBounds(lastX, y, component, size);
          lastX -= myButtonOffset.get();
        }

        if (myErrorComponent != null) {
          x += myOffset.get();

          if (myErrorEnableComponent != null) {
            if (!myButtonComponents.isEmpty()) {
              lastX -= myBeforeButtonOffset.get();
            }

            lastX -= myErrorEnableComponent.getPreferredSize().width;
            lastX -= myOffset.get();
          }

          int errorWidth = lastX - x;
          Dimension size = myErrorComponent.getPreferredSize();

          if (errorWidth >= size.width) {
            setBaselineBounds(x, y, myErrorComponent, size);
            myErrorComponent.setToolTipText(null);
            x += size.width;
          }
          else {
            setBaselineBounds(x, y, myErrorComponent, size, errorWidth, size.height);
            myErrorComponent.setToolTipText(myErrorComponent.getText());
            x += errorWidth;
          }

          if (myErrorEnableComponent != null) {
            x += myOffset.get();
            setBaselineBounds(x, y, myErrorEnableComponent, myErrorEnableComponent.getPreferredSize());
          }
        }
      }

      private void setBaselineBounds(int x, int y, @NotNull Component component, @NotNull Dimension size) {
        setBaselineBounds(x, y, component, size, size.width, size.height);
      }

      private void setBaselineBounds(int x, int y, @NotNull Component component, @NotNull Dimension prefSize, int width, int height) {
        component.setBounds(x, y - component.getBaseline(prefSize.width, prefSize.height), width, height);
      }
    });
  }

  public void setListeners(@NotNull EventHandler eventHandler) {
    myEventHandler = eventHandler;
  }

  @Override
  public Component add(Component component) {
    assert myBaseComponent == null;
    myBaseComponent = component;
    return super.add(component);
  }

  public void addVersionComponent(@NotNull JComponent component) {
    myVersionComponents.add(component);
    add(component, null);
  }

  public void addErrorComponents(@NotNull String message, boolean enableAction, @NotNull Runnable enableCallback) {
    if (myErrorComponent == null) {
      myErrorComponent = new JLabel();
      myErrorComponent.setForeground(DialogWrapper.ERROR_FOREGROUND_COLOR);
      myErrorComponent.setOpaque(false);
      add(myErrorComponent, null);

      if (myEventHandler != null) {
        myEventHandler.add(myErrorComponent);
      }
    }
    myErrorComponent.setText(message);

    if (enableAction) {
      if (myErrorEnableComponent == null) {
        LinkLabel<Object> errorAction = new LinkLabel<>("Enable", null);
        errorAction.setOpaque(false);
        errorAction.setListener((aSource, aLinkData) -> enableCallback.run(), null);
        add(myErrorEnableComponent = errorAction, null);

        if (myEventHandler != null) {
          myEventHandler.add(errorAction);
        }
      }
    }
    else if (myErrorEnableComponent != null) {
      remove(myErrorEnableComponent);
      myErrorEnableComponent = null;
    }

    for (Component component : myVersionComponents) {
      component.setVisible(false);
    }
    doLayout();
  }

  public void removeErrorComponents() {
    if (myErrorComponent != null) {
      remove(myErrorComponent);
      myErrorComponent = null;

      if (myErrorEnableComponent != null) {
        remove(myErrorEnableComponent);
        myErrorEnableComponent = null;
      }

      for (Component component : myVersionComponents) {
        component.setVisible(true);
      }
      doLayout();
    }
  }

  public void addButtonComponent(@NotNull JComponent component) {
    myButtonComponents.add(component);
    add(component, null);
  }

  public void removeButtonComponent(@NotNull JComponent component) {
    myButtonComponents.remove(component);
    remove(component);
  }

  public void setProgressComponent(@NotNull CellPluginComponent pluginComponent, @NotNull JComponent progressComponent) {
    assert myProgressComponent == null;
    myProgressComponent = progressComponent;
    add(progressComponent, null);

    if (myEventHandler != null) {
      myEventHandler.addAll(progressComponent);
      myEventHandler.updateHover(pluginComponent);
    }

    setVisibleOther(false);
    doLayout();
  }

  public void removeProgressComponent() {
    assert myProgressComponent != null;
    remove(myProgressComponent);
    myProgressComponent = null;

    setVisibleOther(true);
    doLayout();
  }

  private void setVisibleOther(boolean value) {
    for (Component component : myVersionComponents) {
      component.setVisible(value);
    }
    if (myErrorComponent != null) {
      myErrorComponent.setVisible(value);
    }
    if (myErrorEnableComponent != null) {
      myErrorEnableComponent.setVisible(value);
    }
    for (Component component : myButtonComponents) {
      component.setVisible(value);
    }
  }
}