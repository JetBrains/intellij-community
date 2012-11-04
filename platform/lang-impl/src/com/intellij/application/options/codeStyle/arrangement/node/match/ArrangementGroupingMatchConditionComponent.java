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

import com.intellij.application.options.codeStyle.arrangement.ArrangementConfigUtil;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Denis Zhdanov
 * @since 9/12/12 5:39 PM
 */
public class ArrangementGroupingMatchConditionComponent extends JPanel implements ArrangementMatchConditionComponent {

  private static final int TOP_INSET = 3;

  @NotNull private final ArrangementAtomMatchCondition myCondition;

  @Nullable private Rectangle myScreenBounds;
  @NotNull private  Dimension myPreferredSize;

  public ArrangementGroupingMatchConditionComponent(@NotNull ArrangementNodeDisplayManager manager,
                                                    @NotNull ArrangementAtomMatchCondition condition)
  {
    myCondition = condition;
    String text = StringUtil.capitalize(StringUtil.pluralize(manager.getDisplayValue(myCondition.getValue())));
    setLayout(new GridBagLayout());
    GridBag constraints = new GridBag().anchor(GridBagConstraints.WEST).weightx(1).insets(TOP_INSET * 2, 20, 0, 0);
    add(new JLabel(String.format("<html><i>%s", text)), constraints);
    myPreferredSize = super.getPreferredSize();
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    return myCondition;
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

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    return myPreferredSize;
  }

  @Override
  public void setSelected(boolean selected) {
  }

  @Nullable
  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    return null;
  }

  @Override
  public void onMouseClick(@NotNull MouseEvent event) {
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Rectangle bounds = getBounds();
    g.setColor(UIManager.getColor("Tree.hash"));
    int cornerX = UIUtil.getTreeLeftChildIndent();
    int y = TOP_INSET;
    g.drawLine(cornerX, y, bounds.width, y);
    g.drawLine(cornerX, y, cornerX, y + bounds.height);

    Point point = ArrangementConfigUtil.getLocationOnScreen(this);
    if (point != null) {
      myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
    }
  }

  @Override
  public Rectangle onMouseEntered(@NotNull MouseEvent e) {
    return null;
  }

  @Nullable
  @Override
  public Rectangle onMouseExited() {
    return null;
  }
}
