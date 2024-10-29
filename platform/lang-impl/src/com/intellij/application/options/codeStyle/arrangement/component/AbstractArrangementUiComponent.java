// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementUiComponent;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractArrangementUiComponent implements ArrangementUiComponent {
  private final @NotNull Set<ArrangementSettingsToken> myAvailableTokens = new HashSet<>();
  private @Nullable Listener  myListener;
  private @Nullable Rectangle myScreenBounds;
  private final @NotNull NotNullLazyValue<JComponent> myComponent = NotNullLazyValue.lazy(() -> {
    JPanel result = new JPanel(new GridBagLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Point point = UIUtil.getLocationOnScreen(this);
        if (point != null) {
          Rectangle bounds = getBounds();
          myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
        }
        if (!myEnabled && g instanceof Graphics2D) {
          ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        }
        super.paintComponent(g);
      }

      @Override
      public boolean isFocusOwner() {
        Component[] components = getComponents();
        if (components != null) {
          for (Component component : components) {
            if (component.isFocusOwner()) {
              return true;
            }
          }
        }
        return false;
      }

      @Override
      public boolean requestFocusInWindow() {
        if (getComponentCount() > 0) {
          return getComponent(0).requestFocusInWindow();
        }
        else {
          return super.requestFocusInWindow();
        }
      }
    };
    result.setOpaque(false);
    result.add(doGetUiComponent(), new GridBag().fillCell());
    return result;
  });

  private boolean myEnabled = true;

  protected AbstractArrangementUiComponent(ArrangementSettingsToken @NotNull ... availableTokens) {
    myAvailableTokens.addAll(Arrays.asList(availableTokens));
  }

  protected AbstractArrangementUiComponent(@NotNull Collection<? extends ArrangementSettingsToken> availableTokens) {
    myAvailableTokens.addAll(availableTokens);
  }

  @Override
  public @NotNull Set<ArrangementSettingsToken> getAvailableTokens() {
    return myAvailableTokens;
  }

  @Override
  public final @NotNull JComponent getUiComponent() {
    return myComponent.getValue();
  }

  protected abstract JComponent doGetUiComponent();

  @Override
  public void setData(@NotNull Object data) {
    // Do nothing
  }

  @Override
  public void setListener(@Nullable Listener listener) {
    myListener = listener;
  }

  @Override
  public @Nullable Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  @Override
  public @Nullable Rectangle onMouseMove(@NotNull MouseEvent event) {
    return null;
  }

  @Override
  public void onMouseRelease(@NotNull MouseEvent event) {
  }

  @Override
  public @Nullable Rectangle onMouseExited() {
    return null;
  }

  @Override
  public @Nullable Rectangle onMouseEntered(@NotNull MouseEvent e) {
    return null;
  }

  protected void fireStateChanged() {
    if (myListener != null) {
      myListener.stateChanged();
    }
  }

  @Override
  public final void reset() {
    setEnabled(false);
    setSelected(false);
    doReset();
  }

  protected abstract void doReset();

  @Override
  public boolean alwaysCanBeActive() {
    return false;
  }
}
