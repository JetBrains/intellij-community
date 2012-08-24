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

import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * {@link ArrangementNodeComponent} for {@link ArrangementAtomMatchCondition} representation.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:06 AM
 */
public class ArrangementAtomNodeComponent implements ArrangementNodeComponent {

  public static final int VERTICAL_PADDING = 2;
  public static final int HORIZONTAL_PADDING = 8;

  @NotNull private final JPanel myRenderer = new JPanel(new GridBagLayout()) {
    @Override
    public void paint(Graphics g) {
      Point point = ArrangementConfigUtil.getLocationOnScreen(this);
      if (point != null) {
        Rectangle bounds = myRenderer.getBounds();
        myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
      }
      if (!myEnabled && g instanceof Graphics2D) {
        ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
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
      return myLabelSize == null ? super.getPreferredSize() : myLabelSize;
    }
  };

  @NotNull private final  ArrangementAtomMatchCondition           myCondition;
  @Nullable private final ActionButton                            myCloseButton;
  @Nullable private final Consumer<ArrangementAtomMatchCondition> myCloseCallback;

  @Nullable private Dimension myLabelSize;
  @Nullable private Rectangle myScreenBounds;

  private boolean myEnabled = true;
  private boolean mySelected;
  private boolean myInverted;
  private boolean myCloseButtonHovered;

  public ArrangementAtomNodeComponent(@NotNull ArrangementNodeDisplayManager manager,
                                      @NotNull ArrangementAtomMatchCondition condition,
                                      @Nullable Consumer<ArrangementAtomMatchCondition> closeCallback)
  {
    myCondition = condition;
    myCloseCallback = closeCallback;
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setText(manager.getDisplayValue(condition));
    myLabelSize = new Dimension(manager.getMaxWidth(condition.getType()), myLabel.getPreferredSize().height);
    
    final ArrangementRemoveConditionAction action;
    if (closeCallback == null) {
      myCloseButton = null;
      action = null;
    }
    else {
      action = new ArrangementRemoveConditionAction();

      Icon buttonIcon = action.getTemplatePresentation().getIcon();
      Dimension buttonSize = new Dimension(buttonIcon.getIconWidth(), buttonIcon.getIconHeight());
      myCloseButton = new ActionButton(action, action.getTemplatePresentation().clone(), ArrangementConstants.RULE_TREE_PLACE, buttonSize) {
        @Override
        protected Icon getIcon() {
          return myCloseButtonHovered ? action.getTemplatePresentation().getHoveredIcon() : action.getTemplatePresentation().getIcon();
        }
      };
    }
    
    GridBagConstraints constraints = new GridBag().anchor(GridBagConstraints.CENTER).insets(0, 0, 0, 0);

    JPanel labelPanel = new JPanel(new GridBagLayout());
    labelPanel.add(myLabel, constraints);
    labelPanel.setBorder(IdeBorderFactory.createEmptyBorder(VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING, HORIZONTAL_PADDING));
    labelPanel.setOpaque(false);

    final int arcSize = myLabel.getFont().getSize();
    JPanel roundBorderPanel = new JPanel(new GridBagLayout()) {
      @Override
      public void paint(Graphics g) {
        Rectangle buttonBounds = getCloseButtonScreenLocation();
        if (buttonBounds != null && action != null) {
          Point mouseScreenLocation = MouseInfo.getPointerInfo().getLocation();
          myCloseButtonHovered = buttonBounds.contains(mouseScreenLocation);
        }
        Color color = mySelected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTabbedPaneBackground();
        Rectangle bounds = getBounds();
        g.setColor(color);
        g.fillRoundRect(0, 0, bounds.width, bounds.height, arcSize, arcSize);
        super.paint(g);
      }
    };
    roundBorderPanel.add(labelPanel, new GridBag().fillCellHorizontally());
    if (myCloseButton != null) {
      roundBorderPanel.add(myCloseButton, new GridBag().anchor(GridBagConstraints.CENTER).insets(VERTICAL_PADDING, 0, 0, 0));
    }
    roundBorderPanel.setBorder(IdeBorderFactory.createRoundedBorder(arcSize));
    roundBorderPanel.setOpaque(false);
    
    myRenderer.setBorder(IdeBorderFactory.createEmptyBorder(VERTICAL_PADDING));
    myRenderer.add(roundBorderPanel, constraints);
    myRenderer.setOpaque(false);
  }

  @NotNull
  @Override
  public ArrangementAtomMatchCondition getMatchCondition() {
    return myCondition;
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
  public ArrangementNodeComponent getNodeComponentAt(@NotNull RelativePoint point) {
    return (myScreenBounds != null && myScreenBounds.contains(point.getScreenPoint())) ? this : null;
  }

  /**
   * Instructs current component that it should {@link #getUiComponent() draw} itself according to the given 'selected' state.
   *
   * @param selected  flag that indicates if current component should be drawn as 'selected'
   */
  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  /**
   * Instructs current component that it should {@link #getUiComponent() draw} itself according to the given 'enabled' state.
   *
   * @param enabled  flag that indicates if current component should be drawn as 'enabled'
   */
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  /**
   * Instructs current component that it should {@link #getUiComponent() draw} itself according to the given 'inverted' state.
   * <p/>
   * For example, target rule might look like 'public' and inverting it produces 'not public'.
   *
   * @param inverted  flag that indicates if current component should be drawn as 'inverted'
   */
  public void setInverted(boolean inverted) {
    myInverted = inverted;
  }

  @Nullable
  @Override
  public Rectangle handleMouseMove(@NotNull MouseEvent event) {
    Rectangle buttonBounds = getCloseButtonScreenLocation();
    if (buttonBounds == null) {
      return null;
    }
    boolean mouseOverButton = buttonBounds.contains(event.getLocationOnScreen());
    return (mouseOverButton ^ myCloseButtonHovered) ? buttonBounds : null;
  }

  @Override
  public void handleMouseClick(@NotNull MouseEvent event) {
    Rectangle buttonBounds = getCloseButtonScreenLocation();
    if (buttonBounds != null && buttonBounds.contains(event.getLocationOnScreen()) && myCloseCallback != null) {
      myCloseCallback.consume(myCondition);
    }
  }

  @Nullable
  private Rectangle getCloseButtonScreenLocation() {
    if (myCloseButton == null || myScreenBounds == null) {
      return null;
    }

    Rectangle buttonBounds = myCloseButton.getBounds();
    buttonBounds = SwingUtilities.convertRectangle(myCloseButton.getParent(), buttonBounds, myRenderer);
    buttonBounds.x += myScreenBounds.x;
    buttonBounds.y += myScreenBounds.y;
    return buttonBounds;
  }

  @Override
  public String toString() {
    return myLabel.getText();
  }
}
