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

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchNodeComponentFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ArrangementUiComponent Component} for showing {@link ArrangementCompositeMatchCondition composite nodes}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:51 AM
 */
public class ArrangementAndMatchConditionComponent extends JPanel implements ArrangementUiComponent {

  @NotNull private final List<ArrangementUiComponent>  myComponents      = ContainerUtilRt.newArrayList();
  @NotNull private final Set<ArrangementSettingsToken> myAvailableTokens = ContainerUtilRt.newHashSet();

  @NotNull private final ArrangementCompositeMatchCondition mySetting;
  @Nullable private      Rectangle                          myScreenBounds;
  @Nullable private      ArrangementUiComponent             myComponentUnderMouse;

  public ArrangementAndMatchConditionComponent(@NotNull StdArrangementMatchRule rule,
                                               @NotNull ArrangementCompositeMatchCondition setting,
                                               @NotNull ArrangementMatchNodeComponentFactory factory,
                                               @NotNull ArrangementStandardSettingsManager manager)
  {
    mySetting = setting;
    setOpaque(false);
    setLayout(new GridBagLayout());
    final Map<ArrangementSettingsToken, ArrangementMatchCondition> operands = ContainerUtilRt.newHashMap();
    ArrangementMatchConditionVisitor visitor = new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        operands.put(condition.getType(), condition);
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        assert false;
      }
    };
    for (ArrangementMatchCondition operand : setting.getOperands()) {
      operand.invite(visitor);
    }

    List<ArrangementSettingsToken> ordered = manager.sort(operands.keySet());
    GridBagConstraints constraints = new GridBag().anchor(GridBagConstraints.EAST).insets(0, 0, 0, ArrangementConstants.HORIZONTAL_GAP);
    for (ArrangementSettingsToken key : ordered) {
      ArrangementMatchCondition operand = operands.get(key);
      assert operand != null;
      ArrangementUiComponent component = factory.getComponent(operand, rule, true);
      myComponents.add(component);
      myAvailableTokens.addAll(component.getAvailableTokens());
      JComponent uiComponent = component.getUiComponent();
      add(uiComponent, constraints);
    }
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    return mySetting;
  }

  @Override
  public void setData(@NotNull Object data) {
    // Do nothing
  }

  @NotNull
  @Override
  public JComponent getUiComponent() {
    return this;
  }

  @Nullable
  @Override
  public Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  @Override
  public void setSelected(boolean selected) {
    for (ArrangementUiComponent component : myComponents) {
      component.setSelected(selected);
    }
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public void paint(Graphics g) {
    Point point = UIUtil.getLocationOnScreen(this);
    if (point != null) {
      Rectangle bounds = getBounds();
      myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
    }
    super.paint(g);
  }

  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    Point location = event.getLocationOnScreen();
    for (ArrangementUiComponent component : myComponents) {
      Rectangle bounds = component.getScreenBounds();
      if (bounds == null || !bounds.contains(location)) {
        continue;
      }
      if (myComponentUnderMouse == null) {
        myComponentUnderMouse = component;
        Rectangle rectangleOnEnter = myComponentUnderMouse.onMouseEntered(event);
        Rectangle rectangleOnMove = myComponentUnderMouse.onMouseMove(event);
        if (rectangleOnEnter != null && rectangleOnMove != null) {
          return myScreenBounds; // Repaint row
        }
        else if (rectangleOnEnter != null) {
          return rectangleOnEnter;
        }
        else {
          return rectangleOnMove;
        }
      }
      else {
        if (myComponentUnderMouse != component) {
          myComponentUnderMouse.onMouseExited();
          myComponentUnderMouse = component;
          component.onMouseEntered(event);
          return myScreenBounds; // Repaint row.
        }
        else {
          return component.onMouseMove(event);
        }
      }
    }
    if (myComponentUnderMouse == null) {
      return null;
    }
    else {
      Rectangle result = myComponentUnderMouse.onMouseExited();
      myComponentUnderMouse = null;
      return result;
    }
  }

  @Override
  public void onMouseRelease(@NotNull MouseEvent event) {
    Point location = event.getLocationOnScreen();
    for (ArrangementUiComponent component : myComponents) {
      Rectangle bounds = component.getScreenBounds();
      if (bounds != null && bounds.contains(location)) {
        component.onMouseRelease(event);
        return;
      }
    }
  }

  @Override
  public Rectangle onMouseEntered(@NotNull MouseEvent event) {
    Point location = event.getLocationOnScreen();
    for (ArrangementUiComponent component : myComponents) {
      Rectangle bounds = component.getScreenBounds();
      if (bounds != null && bounds.contains(location)) {
        myComponentUnderMouse = component;
        return component.onMouseEntered(event);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Rectangle onMouseExited() {
    if (myComponentUnderMouse != null) {
      Rectangle result = myComponentUnderMouse.onMouseExited();
      myComponentUnderMouse = null;
      return result;
    }
    return null;
  }

  @Nullable
  @Override
  public ArrangementSettingsToken getToken() {
    return myComponentUnderMouse == null ? null : myComponentUnderMouse.getToken();
  }

  @NotNull
  @Override
  public Set<ArrangementSettingsToken> getAvailableTokens() {
    return myAvailableTokens;
  }

  @Override
  public void chooseToken(@NotNull ArrangementSettingsToken data) throws IllegalArgumentException, UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSelected() {
    return myComponentUnderMouse != null && myComponentUnderMouse.isSelected();
  }

  @Override
  public void reset() {
    for (ArrangementUiComponent component : myComponents) {
      component.reset();
    }
  }

  @Override
  public int getBaselineToUse(int width, int height) {
    return -1;
  }

  @Override
  public void setListener(@NotNull Listener listener) {
    for (ArrangementUiComponent component : myComponents) {
      component.setListener(listener);
    } 
  }

  @Override
  public String toString() {
    return String.format("(%s)", StringUtil.join(myComponents, " and "));
  }
}
