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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.ui.awt.RelativePoint;
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
public class ArrangementEditIconMatchNodeComponent extends JPanel implements ArrangementMatchNodeComponent {

  @NotNull private final ArrangementMatchNodeComponent myDelegate;
  @NotNull private final Icon                          myEditIcon;
  @NotNull private final ActionButton                  myEditButton;

  public ArrangementEditIconMatchNodeComponent(@NotNull ArrangementMatchNodeComponent delegate) {
    myDelegate = delegate;
    setLayout(null);
    JComponent delegateComponent = myDelegate.getUiComponent();
    add(delegateComponent);
    Dimension size = delegateComponent.getPreferredSize();
    delegateComponent.setBounds(0, 0, size.width, size.height);

    AnAction action = ActionManager.getInstance().getAction("Arrangement.Rule.Edit");
    Presentation presentation = action.getTemplatePresentation().clone();
    myEditIcon = presentation.getIcon();
    Dimension buttonSize = new Dimension(myEditIcon.getIconWidth(), myEditIcon.getIconHeight());
    myEditButton = new ActionButton(action, presentation, ArrangementConstants.RULE_TREE_PLACE, buttonSize);
    add(myEditButton);
    myEditButton.setBounds(size.width, 0, myEditIcon.getIconWidth(), myEditIcon.getIconHeight());
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
  public ArrangementMatchNodeComponent getNodeComponentAt(@NotNull RelativePoint point) {
    return myDelegate.getNodeComponentAt(point);
  }

  @Override
  @Nullable
  public Rectangle getScreenBounds() {
    return myDelegate.getScreenBounds();
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
    myEditButton.setBounds(width - myEditIcon.getIconWidth() - ArrangementAtomMatchNodeComponent.HORIZONTAL_PADDING,
                           0,
                           myEditIcon.getIconWidth(),
                           myEditIcon.getIconHeight());
    return true;
  }

  @Override
  public void setSelected(boolean selected) {
    myDelegate.setSelected(selected);
  }

  @Override
  @Nullable
  public Rectangle handleMouseMove(@NotNull MouseEvent event) {
    return myDelegate.handleMouseMove(event);
  }

  @Override
  public void handleMouseClick(@NotNull MouseEvent event) {
    myDelegate.handleMouseClick(event);
  }
}
