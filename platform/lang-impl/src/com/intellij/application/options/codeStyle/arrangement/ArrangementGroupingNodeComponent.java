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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.ui.awt.RelativePoint;
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
public class ArrangementGroupingNodeComponent extends JPanel implements ArrangementNodeComponent {

  @NotNull private final ArrangementColorsService myColorsService = ServiceManager.getService(ArrangementColorsService.class);
  @NotNull private final ArrangementAtomMatchCondition myCondition;
  @Nullable private Rectangle myScreenBounds;
  private           boolean   mySelected;

  public ArrangementGroupingNodeComponent(@NotNull ArrangementNodeDisplayManager manager,
                                          @NotNull ArrangementAtomMatchCondition condition)
  {
    myCondition = condition;
    String text = StringUtil.capitalize(StringUtil.pluralize(manager.getDisplayValue(myCondition.getValue())));
    setLayout(new GridBagLayout());
    add(new JLabel(String.format("<html><i>%s", text)), new GridBag().anchor(GridBagConstraints.CENTER).weightx(1).insets(0, 12, 0, 0));
    Dimension size = getPreferredSize();
    setPreferredSize(new Dimension(manager.getMaxGroupTextWidth() * 5, size.height * 2));
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
  public ArrangementNodeComponent getNodeComponentAt(@NotNull RelativePoint point) {
    return (myScreenBounds != null && myScreenBounds.contains(point.getScreenPoint())) ? this : null;
  }

  @Nullable
  @Override
  public Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  @Override
  public void setScreenBounds(@Nullable Rectangle bounds) {
    myScreenBounds = bounds; 
  }

  @Override
  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  @Nullable
  @Override
  public Rectangle handleMouseMove(@NotNull MouseEvent event) {
    return null;
  }

  @Override
  public void handleMouseClick(@NotNull MouseEvent event) {
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Rectangle bounds = getBounds();
    g.setColor(UIManager.getColor("Tree.hash"));
    int cornerX = UIUtil.getTreeLeftChildIndent();
    int y = 3;
    g.drawLine(cornerX, y, bounds.width, y);
    g.drawLine(cornerX, y, cornerX, y + bounds.height);
    if (mySelected) {
      g.setColor(myColorsService.getBackgroundColor(true));
      g.fillRect(cornerX + 1, y + 1, bounds.width - 1, bounds.height - 1);
    }

    Point point = ArrangementConfigUtil.getLocationOnScreen(this);
    if (point != null) {
      myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
    }
  }
}
