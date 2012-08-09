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

/**
 * // TODO den add doc
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:51 AM
 */
public class ArrangementAndRenderer extends JPanel implements ArrangementNodeRenderer<ArrangementSettingsCompositeNode> {

  private static final int BUBBLE_CONNECTOR_LENGTH = 10;

  @NotNull private final ArrangementNodeRenderingContext myContext;

  public ArrangementAndRenderer(@NotNull ArrangementNodeRenderingContext context) {
    myContext = context;
    setLayout(null);
    setOpaque(true);
  }

  @NotNull
  @Override
  public JComponent getRendererComponent(@NotNull ArrangementSettingsCompositeNode node) {
    removeAll();
    int x = 0;
    for (ArrangementSettingsNode operand : node.getOperands()) {
      JComponent component = myContext.getRenderer(operand).getRendererComponent(operand);
      Dimension size = component.getPreferredSize();
      add(component);
      component.setBounds(x, 0, size.width, size.height);
      x += size.width + BUBBLE_CONNECTOR_LENGTH;
    }
    return this;
  }

  @Override
  public void reset() {
    invalidate();
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
    Component[] components = getComponents();
    for (Component component : components) {
      Dimension size = component.getPreferredSize();
      myWidth += size.width;
      myHeight = Math.max(size.height, myHeight);
    }
    if (components.length > 1) {
      myWidth += (components.length - 1) * BUBBLE_CONNECTOR_LENGTH;
    }
    return new Dimension(myWidth, myHeight);
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    
    Component[] components = getComponents();
    if (components.length < 2) {
      return;
    }

    // Draw node connectors.
    int x = 0;
    g.setColor(UIManager.getColor("Tree.hash"));
    for (int i = 0; i < components.length - 1; i++) {
      Component component = components[i];
      Rectangle bounds = component.getBounds();
      int y = bounds.y + bounds.height / 2;
      // TODO den shift y for the component
      x += bounds.width;
      // TODO den use right 'y'
      g.drawLine(x, y, x + BUBBLE_CONNECTOR_LENGTH, y);
      x += BUBBLE_CONNECTOR_LENGTH;
    }
  }
}
