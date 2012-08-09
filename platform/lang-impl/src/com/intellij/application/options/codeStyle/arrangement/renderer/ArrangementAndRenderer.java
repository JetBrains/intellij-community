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
package com.intellij.application.options.codeStyle.arrangement.renderer;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsCompositeNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * // TODO den add doc
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:51 AM
 */
public class ArrangementAndRenderer extends JPanel implements ArrangementNodeRenderer<ArrangementSettingsCompositeNode> {

  private static final int BUBBLE_CONNECTOR_LENGTH = 20;

  @NotNull private final ArrangementNodeRenderingContext myContext;
  @NotNull private final List<JComponent> myOperands = new ArrayList<JComponent>();

  public ArrangementAndRenderer(@NotNull ArrangementNodeRenderingContext context) {
    myContext = context;
  }

  @Override
  public JComponent getRendererComponent(@NotNull ArrangementSettingsCompositeNode node) {
    myOperands.clear();
    for (ArrangementSettingsNode operand : node.getOperands()) {
      myOperands.add(myContext.getRenderer(operand).getRendererComponent(operand));
    }
    return this;
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
    int myWidth = 0;
    int myHeight = 0;
    for (JComponent operand : myOperands) {
      Dimension size = operand.getPreferredSize();
      myWidth += size.width;
      myHeight += size.height;
    }
    if (myOperands.size() > 1) {
      myWidth += (myOperands.size() - 1) * BUBBLE_CONNECTOR_LENGTH;
    }
    return new Dimension(myWidth, myHeight);
  }

  @Override
  protected void paintComponent(Graphics g) {
    int x = 0;
    for (int i = 0; i < myOperands.size(); i++) {
      JComponent component = myOperands.get(i);
      // TODO den shift y for the component
      component.paint(g);
      x += component.getBounds().width;
      if (i < myOperands.size() - 1) {
        // TODO den implement right color pick up
        g.setColor(Color.RED);
        // TODO den use right 'y'
        g.drawLine(x, 5, x + BUBBLE_CONNECTOR_LENGTH, 5);
      }
    }
  }

  @Override
  public void paint(Graphics g) {
    int x = 0;
    for (int i = 0; i < myOperands.size(); i++) {
      JComponent component = myOperands.get(i);
      Dimension size = component.getPreferredSize();
      component.setBounds(x, 0, size.width, size.height);
      // TODO den shift y for the component
      component.paint(g);
      x += size.width;
      if (i < myOperands.size() - 1) {
        // TODO den implement right color pick up
        g.setColor(Color.RED);
        // TODO den use right 'y'
        g.drawLine(x, 5, x + BUBBLE_CONNECTOR_LENGTH, 5);
      }
    }
  }
}
