/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.application.options.codeStyle.arrangement.util.ArrangementConfigUtil;
import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ArrangementMatchConditionComponent Component} for showing {@link ArrangementCompositeMatchCondition composite nodes}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:51 AM
 */
public class ArrangementAndMatchConditionComponent extends JPanel implements ArrangementMatchConditionComponent {

  @NotNull private final List<ArrangementMatchConditionComponent> myComponents = new ArrayList<ArrangementMatchConditionComponent>();

  @NotNull private final ArrangementCompositeMatchCondition mySetting;
  @Nullable private      Rectangle                          myScreenBounds;
  @Nullable private      ArrangementMatchConditionComponent myComponentUnderMouse;

  public ArrangementAndMatchConditionComponent(@NotNull StdArrangementMatchRule rule,
                                               @NotNull ArrangementCompositeMatchCondition setting,
                                               @NotNull ArrangementMatchNodeComponentFactory factory,
                                               @NotNull ArrangementNodeDisplayManager manager)
  {
    mySetting = setting;
    setOpaque(false);
    setLayout(new GridBagLayout());
    final Map<Object, ArrangementMatchCondition> operands = new HashMap<Object, ArrangementMatchCondition>();
    ArrangementMatchConditionVisitor visitor = new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        operands.put(condition.getValue(), condition);
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        operands.put(condition, condition);
      }
    };
    for (ArrangementMatchCondition operand : setting.getOperands()) {
      operand.invite(visitor);
    }

    List<Object> ordered = manager.sort(operands.keySet());
    GridBagConstraints constraints = new GridBag().anchor(GridBagConstraints.EAST).insets(0, 0, 0, ArrangementConstants.HORIZONTAL_GAP);
    for (Object key : ordered) {
      ArrangementMatchCondition operand = operands.get(key);
      assert operand != null;
      ArrangementMatchConditionComponent component = factory.getComponent(operand, rule, true);
      myComponents.add(component);
      JComponent uiComponent = component.getUiComponent();
      add(uiComponent, constraints);
    }
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    return mySetting;
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
    for (ArrangementMatchConditionComponent component : myComponents) {
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
    Point point = ArrangementConfigUtil.getLocationOnScreen(this);
    if (point != null) {
      Rectangle bounds = getBounds();
      myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
    }
    super.paint(g);
  }

  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    Point location = event.getLocationOnScreen();
    for (ArrangementMatchConditionComponent component : myComponents) {
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
    for (ArrangementMatchConditionComponent component : myComponents) {
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
    for (ArrangementMatchConditionComponent component : myComponents) {
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

  @Override
  public String toString() {
    return String.format("(%s)", StringUtil.join(myComponents, " and "));
  }
}
