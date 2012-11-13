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
package com.intellij.application.options.codeStyle.arrangement.util;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementMatchConditionComponent;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Denis Zhdanov
 * @since 10/31/12 5:00 PM
 */
public class ArrangementListRowDecorator extends JPanel implements ArrangementMatchConditionComponent {

  @NotNull private final ArrangementRuleIndexControl        myRowIndexControl;
  @NotNull private final ArrangementMatchConditionComponent myDelegate;
  @NotNull private final ArrangementMatchingRulesControl    myControl;
  @NotNull private final MyActionButton                     myEditButton;

  @Nullable private Rectangle myScreenBounds;

  private boolean myBeingEdited;
  private boolean myUnderMouse;

  public ArrangementListRowDecorator(@NotNull ArrangementMatchConditionComponent delegate,
                                     @NotNull ArrangementMatchingRulesControl control)
  {
    myDelegate = delegate;
    myControl = control;

    AnAction action = ActionManager.getInstance().getAction("Arrangement.Rule.Edit");
    Presentation presentation = action.getTemplatePresentation().clone();
    Icon editIcon = presentation.getIcon();
    Dimension buttonSize = new Dimension(editIcon.getIconWidth(), editIcon.getIconHeight());
    myEditButton = new MyActionButton(action, presentation, ArrangementConstants.MATCHING_RULES_CONTROL_PLACE, buttonSize);
    myEditButton.setVisible(false);

    FontMetrics metrics = getFontMetrics(getFont());
    int maxWidth = 0;
    for (int i = 0; i <= 99; i++) {
      maxWidth = Math.max(metrics.stringWidth(String.valueOf(i)), maxWidth);
    }
    int height = metrics.getHeight() - metrics.getDescent() - metrics.getLeading();
    int diameter = Math.max(maxWidth, height) * 5 / 3;
    myRowIndexControl = new ArrangementRuleIndexControl(diameter, height);

    setOpaque(true);
    init();
  }

  private void init() {
    setLayout(new GridBagLayout());
    GridBag constraints = new GridBag().anchor(GridBagConstraints.CENTER)
      .insets(0, ArrangementConstants.HORIZONTAL_PADDING, 0, ArrangementConstants.HORIZONTAL_GAP * 2);
    add(myRowIndexControl, constraints);
    add(myDelegate.getUiComponent(), new GridBag().weightx(1).anchor(GridBagConstraints.WEST));
    add(myEditButton, new GridBag().anchor(GridBagConstraints.EAST));
    setBorder(IdeBorderFactory.createEmptyBorder(ArrangementConstants.VERTICAL_GAP));
  }

  @Override
  protected void paintComponent(Graphics g) {
    Point point = ArrangementConfigUtil.getLocationOnScreen(this);
    if (point != null) {
      Rectangle bounds = getBounds();
      myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
    }
    
    FontMetrics metrics = g.getFontMetrics();
    int baseLine = SimpleColoredComponent.getTextBaseLine(metrics, metrics.getHeight());
    myRowIndexControl.setBaseLine(baseLine + ArrangementConstants.VERTICAL_GAP + myDelegate.getUiComponent().getBounds().y - myRowIndexControl.getBounds().y);
    super.paintComponent(g);
  }

  public void setRowIndex(int row) {
    myRowIndexControl.setIndex(row);
  }

  public void setUnderMouse(boolean underMouse) {
    myUnderMouse = underMouse;
    if (myUnderMouse) {
      setBackground(UIUtil.getDecoratedRowColor());
    }
    else {
      setBackground(UIUtil.getListBackground());
    }
  }

  public void setBeingEdited(boolean beingEdited) {
    if (myBeingEdited && !beingEdited) {
      myEditButton.getPresentation().putClientProperty(Toggleable.SELECTED_PROPERTY, false);
    }
    if (!beingEdited && !myUnderMouse) {
      myEditButton.setVisible(false);
    }
    myBeingEdited = beingEdited;
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    return myDelegate.getMatchCondition();
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
    myDelegate.setSelected(selected); 
  }

  @Override
  public Rectangle onMouseEntered(@NotNull MouseEvent e) {
    setBackground(UIUtil.getDecoratedRowColor());
    myEditButton.setVisible(myControl.getSelectedModelRows().size() <= 1);
    return myDelegate.onMouseEntered(e);
  }

  @Nullable
  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    myEditButton.setVisible(myControl.getSelectedModelRows().size() <= 1);
    Rectangle bounds = getButtonScreenBounds();
    if (!myBeingEdited && bounds != null) {
      boolean selected = bounds.contains(event.getLocationOnScreen());
      boolean wasSelected = myEditButton.getPresentation().getClientProperty(Toggleable.SELECTED_PROPERTY) == Boolean.TRUE;
      myEditButton.getPresentation().putClientProperty(Toggleable.SELECTED_PROPERTY, selected);
      if (selected ^ wasSelected) {
        return myScreenBounds;
      }
    }

    return myDelegate.onMouseMove(event);
  }

  @Override
  public void onMouseRelease(@NotNull MouseEvent event) {
    myEditButton.setVisible(myControl.getSelectedModelRows().size() <= 1);
    Rectangle bounds = getButtonScreenBounds();
    if (bounds != null && bounds.contains(event.getLocationOnScreen())) {
      if (myBeingEdited) {
        myControl.hideEditor();
        myBeingEdited = false;
      }
      else {
        int row = myControl.getRowByRenderer(this);
        if (row >= 0) {
          myControl.showEditor(row);
          myBeingEdited = true;
        }
      }
      event.consume();
      return;
    }
    myDelegate.onMouseRelease(event); 
  }

  @Nullable
  @Override
  public Rectangle onMouseExited() {
    setBackground(UIUtil.getListBackground());
    if (!myBeingEdited) {
      myEditButton.setVisible(false);
    }
    return myDelegate.onMouseExited(); 
  }
  
  @Nullable
  private Rectangle getButtonScreenBounds() {
    if (myScreenBounds == null) {
      return null;
    }
    Rectangle bounds = myEditButton.getBounds();
    return new Rectangle(bounds.x + myScreenBounds.x, bounds.y + myScreenBounds.y, bounds.width, bounds.height); 
  }
  
  @Override
  public String toString() {
    return "list row decorator for " + myDelegate.toString();
  }
  
  private static class MyActionButton extends ActionButton {
    MyActionButton(AnAction action, Presentation presentation, String place, @NotNull Dimension minimumSize) {
      super(action, presentation, place, minimumSize);
    }

    @NotNull
    public Presentation getPresentation() {
      return myPresentation;
    }
  }
}
