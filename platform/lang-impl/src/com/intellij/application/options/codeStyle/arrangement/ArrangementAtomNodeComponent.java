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

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:06 AM
 */
public class ArrangementAtomNodeComponent implements ArrangementNodeComponent {

  private static final int PADDING = 2;

  @Nullable private Rectangle myScreenBounds;
  @NotNull private final JPanel myRenderer = new JPanel(new GridBagLayout()) {
    @Override
    public void paint(Graphics g) {
      Point point = ArrangementSettingsUtil.getLocationOnScreen(this);
      if (point != null) {
        Rectangle bounds = myRenderer.getBounds();
        myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
      }
      super.paint(g);
    }
  };
  
  @NotNull private final JLabel myLabel = new JLabel() {
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
      return mySize == null ? super.getPreferredSize() : mySize;
    }
  };
  @Nullable private Dimension mySize;

  public ArrangementAtomNodeComponent(@NotNull ArrangementNodeDisplayManager manager, @NotNull ArrangementSettingsAtomNode node) {
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setText(manager.getDisplayValue(node));
    mySize = new Dimension(manager.getMaxWidth(node.getType()), myLabel.getPreferredSize().height);
    
    GridBagConstraints constraints = new GridBag().anchor(GridBagConstraints.CENTER).insets(0, 0, 0, 0);

    JPanel labelPanel = new JPanel(new GridBagLayout());
    myLabel.setBackground(Color.red);
    labelPanel.add(myLabel, constraints);
    labelPanel.setBorder(IdeBorderFactory.createEmptyBorder(PADDING));
    labelPanel.setOpaque(false);

    final int arcSize = myLabel.getFont().getSize();
    JPanel roundBorderPanel = new JPanel(new GridBagLayout()) {
      @Override
      public void paint(Graphics g) {
        Color color;
        if (myScreenBounds != null && myScreenBounds.contains(MouseInfo.getPointerInfo().getLocation())) {
          color = UIUtil.getTreeSelectionBackground();
        }
        else {
          color = UIUtil.getTabbedPaneBackground();
        }
        Rectangle bounds = getBounds();
        g.setColor(color);
        g.fillRoundRect(0, 0, bounds.width, bounds.height, arcSize, arcSize);
        super.paint(g);
      }
    };
    roundBorderPanel.add(labelPanel);
    roundBorderPanel.setBorder(IdeBorderFactory.createRoundedBorder(arcSize));
    roundBorderPanel.setOpaque(false);
    
    myRenderer.setBorder(IdeBorderFactory.createEmptyBorder(PADDING));
    myRenderer.add(roundBorderPanel, constraints);
    myRenderer.setOpaque(false);
  }

  @NotNull
  @Override
  public JComponent getUiComponent() {
    return myRenderer;
  }

  @Nullable
  @Override
  public Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  @Override
  public void setScreenBounds(@Nullable Rectangle screenBounds) {
    myScreenBounds = screenBounds;
  }

  @Override
  public ArrangementNodeComponent getComponentAt(@NotNull RelativePoint point) {
    return (myScreenBounds != null && myScreenBounds.contains(point.getScreenPoint())) ? this : null;
  }
  
  @Override
  public String toString() {
    return myLabel.getText();
  }
}
