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
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.util.ArrangementConfigUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 11/15/12 1:01 PM
 */
public class ArrangementOrderTypeComponent extends JPanel {

  @NotNull
  private final SimpleColoredComponent myTextControl = new SimpleColoredComponent() {
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override public Dimension getPreferredSize() { return myTextControlSize == null ? super.getPreferredSize() : myTextControlSize; }

    @Override public String toString() { return "text component for " + this; }
  };
  @NotNull private final  ArrangementEntryOrderType myOrderType;
  @NotNull private final  ArrangementColorsProvider myColorsProvider;
  @NotNull private final  String                    myText;
  @NotNull private final  SideBorder                myBorder;
  @Nullable private final Dimension                 myTextControlSize;
  @Nullable private       Rectangle                 myScreenBounds;

  public ArrangementOrderTypeComponent(@NotNull ArrangementEntryOrderType orderType,
                                       @NotNull ArrangementNodeDisplayManager displayManager,
                                       @NotNull ArrangementColorsProvider colorsProvider,
                                       int width)
  {
    super(new GridBagLayout());
    myOrderType = orderType;
    myColorsProvider = colorsProvider;
    myTextControl.setTextAlign(SwingConstants.CENTER);
    myText = displayManager.getDisplayValue(orderType);
    TextAttributes attributes = colorsProvider.getTextAttributes(ArrangementSettingType.ORDER, false);
    myTextControl.append(myText, SimpleTextAttributes.fromTextAttributes(attributes));
    myTextControlSize = new Dimension(width, myTextControl.getPreferredSize().height);

    add(myTextControl, new GridBag().anchor(GridBagConstraints.WEST));
    setBorder(myBorder = (SideBorder)IdeBorderFactory.createBorder());
  }

  public void setSelected(boolean selected) {
    myTextControl.clear();
    TextAttributes attributes = myColorsProvider.getTextAttributes(ArrangementSettingType.ORDER, selected);
    myTextControl.append(myText, SimpleTextAttributes.fromTextAttributes(attributes));
    myBorder.setLineColor(myColorsProvider.getBorderColor(selected));
    Color myBackgroundColor = attributes.getBackgroundColor();
    myTextControl.setBackground(myBackgroundColor);
  }

  @Override
  protected void paintComponent(Graphics g) {
    Point point = ArrangementConfigUtil.getLocationOnScreen(this);
    if (point != null) {
      Rectangle bounds = getBounds();
      myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
    }
    super.paintComponent(g);
  }

  @NotNull
  public ArrangementEntryOrderType getOrderType() {
    return myOrderType;
  }

  @Nullable
  public Rectangle getScreenBounds() {
    return myScreenBounds;
  }
}
