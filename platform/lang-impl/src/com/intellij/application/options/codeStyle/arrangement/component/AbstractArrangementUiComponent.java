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
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementUiComponent;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 3/11/13 10:41 AM
 */
public abstract class AbstractArrangementUiComponent implements ArrangementUiComponent {

  @NotNull private final NotNullLazyValue<JComponent> myComponent = new NotNullLazyValue<JComponent>() {
    @NotNull
    @Override
    protected JComponent compute() {
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
    }
  };

  @NotNull private final Set<ArrangementSettingsToken> myAvailableTokens = ContainerUtilRt.newHashSet();

  @Nullable private Listener  myListener;
  @Nullable private Rectangle myScreenBounds;

  private boolean myEnabled = true;

  protected AbstractArrangementUiComponent(@NotNull ArrangementSettingsToken ... availableTokens) {
    myAvailableTokens.addAll(Arrays.asList(availableTokens));
  }

  protected AbstractArrangementUiComponent(@NotNull Collection<ArrangementSettingsToken> availableTokens) {
    myAvailableTokens.addAll(availableTokens);
  }

  @NotNull
  @Override
  public Set<ArrangementSettingsToken> getAvailableTokens() {
    return myAvailableTokens;
  }

  @NotNull
  @Override
  public final JComponent getUiComponent() {
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

  @Nullable
  @Override
  public Rectangle getScreenBounds() {
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

  @Nullable
  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    return null;
  }

  @Override
  public void onMouseRelease(@NotNull MouseEvent event) {
  }

  @Nullable
  @Override
  public Rectangle onMouseExited() {
    return null;
  }

  @Nullable
  @Override
  public Rectangle onMouseEntered(@NotNull MouseEvent e) {
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
}
