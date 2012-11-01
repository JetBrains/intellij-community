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
package com.intellij.application.options.codeStyle.arrangement.node.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * UI decorator component which allows to draw 'edit' icon at the right screen edge.
 * 
 * @author Denis Zhdanov
 * @since 10/29/12 11:53 AM
 */
public class ArrangementEditIconMatchConditionComponent extends JPanel implements ArrangementMatchConditionComponent {

  @NotNull private final ArrangementMatchConditionComponent myDelegate;
  @NotNull private final Icon                               myActiveEditIcon;
  @NotNull private final Icon                               myInactiveEditIcon;
  @NotNull private final ActionButton                       myEditButton;

  public ArrangementEditIconMatchConditionComponent(@NotNull ArrangementMatchConditionComponent delegate) {
    myDelegate = delegate;

    setLayout(null);
    JComponent delegateComponent = myDelegate.getUiComponent();
    add(delegateComponent);
    Dimension size = delegateComponent.getPreferredSize();
    delegateComponent.setBounds(0, 0, size.width, size.height);

    AnAction action = ActionManager.getInstance().getAction("Arrangement.Rule.Edit");
    Presentation presentation = action.getTemplatePresentation().clone();
    myActiveEditIcon = presentation.getIcon();
    myInactiveEditIcon = AllIcons.Actions.Edit;
    Dimension buttonSize = new Dimension(myActiveEditIcon.getIconWidth(), myActiveEditIcon.getIconHeight());
    myEditButton = new ActionButton(action, presentation, ArrangementConstants.RULE_TREE_PLACE, buttonSize) {
      @Override
      protected Icon getIcon() {
        Rectangle bounds = getScreenBounds();
        if (bounds == null) {
          return myInactiveEditIcon;
        }
        Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
        if (bounds.contains(mouseLocation)) {
          return myActiveEditIcon;
        }
        else {
          return myInactiveEditIcon;
        }
      }
    };
    add(myEditButton);
    int y = 0;
    if (size.height > myActiveEditIcon.getIconHeight()) {
      y = (size.height - myActiveEditIcon.getIconHeight()) / 2;
    }
    myEditButton.setBounds(size.width, y, myActiveEditIcon.getIconWidth(), myActiveEditIcon.getIconHeight());
  }

  @Override
  @NotNull
  public ArrangementMatchCondition getMatchCondition() {
    return myDelegate.getMatchCondition();
  }

  @Override
  @NotNull
  public JComponent getUiComponent() {
    return this;
  }

  @Override
  @Nullable
  public Rectangle getScreenBounds() {
    Rectangle delegateBounds = myDelegate.getScreenBounds();
    if (delegateBounds == null) {
      return null;
    }
    Rectangle buttonBounds = myEditButton.getBounds();
    int y = delegateBounds.y;
    int yDiff = buttonBounds.height - delegateBounds.height;
    if (yDiff > 0) {
      y -= yDiff;
    }
    int width = buttonBounds.x + buttonBounds.width + ArrangementConstants.HORIZONTAL_PADDING;
    return new Rectangle(delegateBounds.x, y, width, Math.max(delegateBounds.height, buttonBounds.height));
  }

  @Nullable
  private Rectangle getEditButtonScreenBounds() {
    Rectangle delegateScreenBounds = myDelegate.getScreenBounds();
    if (delegateScreenBounds == null) {
      return null;
    }

    Rectangle buttonBounds = myEditButton.getBounds();
    if (buttonBounds == null) {
      return null;
    }

    int y = delegateScreenBounds.y;
    int yDiff = buttonBounds.height - delegateScreenBounds.height;
    if (yDiff > 0) {
      y -= yDiff;
    }
    return new Rectangle(delegateScreenBounds.x + buttonBounds.x, y, buttonBounds.width, buttonBounds.height);
  }
  
  @Override
  public void setScreenBounds(@Nullable Rectangle bounds) {
    myDelegate.setScreenBounds(bounds);
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
  public Dimension getPreferredSize() {
    Dimension size = myDelegate.getUiComponent().getPreferredSize();
    Rectangle bounds = myEditButton.getBounds();
    return new Dimension(bounds.x + bounds.width, size.height);
  }
  
  @Override
  public boolean onCanvasWidthChange(int width) {
    myDelegate.onCanvasWidthChange(width);
    if (width > myDelegate.getUiComponent().getPreferredSize().width) {
      myEditButton.setBounds(width - myActiveEditIcon.getIconWidth() - ArrangementConstants.HORIZONTAL_PADDING,
                             myEditButton.getBounds().y,
                             myActiveEditIcon.getIconWidth(),
                             myActiveEditIcon.getIconHeight());
    }
    return true;
  }

  @Override
  public void setSelected(boolean selected) {
    myDelegate.setSelected(selected);
  }

  @Override
  @Nullable
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    return myDelegate.onMouseMove(event);
  }

  @Override
  public void onMouseClick(@NotNull MouseEvent event) {
    myDelegate.onMouseClick(event);
  }

  @Override
  public String toString() {
    return "'edit' decorator for " + myDelegate.toString();
  }

  @Override
  public void onMouseExited() {
  }
}
