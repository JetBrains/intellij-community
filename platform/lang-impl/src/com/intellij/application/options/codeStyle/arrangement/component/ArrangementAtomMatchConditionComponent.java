/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.action.ArrangementRemoveConditionAction;
import com.intellij.application.options.codeStyle.arrangement.animation.ArrangementAnimationPanel;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.util.InsetsPanel;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Set;

/**
 * {@link ArrangementUiComponent} for {@link ArrangementAtomMatchCondition} representation.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 8/8/12 10:06 AM
 */
public class ArrangementAtomMatchConditionComponent implements ArrangementUiComponent {

  @NotNull private static final BorderStrategy TEXT_BORDER_STRATEGY       = new NameBorderStrategy();
  @NotNull private static final BorderStrategy PREDEFINED_BORDER_STRATEGY = new PredefinedConditionBorderStrategy();

  @NotNull
  private final SimpleColoredComponent myTextControl = new SimpleColoredComponent() {
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
      return myTextControlSize == null ? super.getPreferredSize() : myTextControlSize;
    }

    @Override
    public String toString() {
      return "text component for " + myText;
    }
  };

  @NotNull private final Set<ArrangementSettingsToken> myAvailableTokens = ContainerUtilRt.newHashSet();

  @NotNull private final BorderStrategy                myBorderStrategy;
  @NotNull private final String                        myText;
  @NotNull private final ArrangementColorsProvider     myColorsProvider;
  @NotNull private final RoundedLineBorder             myBorder;
  @NotNull private final ArrangementAtomMatchCondition myCondition;
  @NotNull private final ArrangementAnimationPanel     myAnimationPanel;

  @Nullable private final ActionButton                                     myCloseButton;
  @Nullable private final Rectangle                                        myCloseButtonBounds;
  @Nullable private final Consumer<ArrangementAtomMatchConditionComponent> myCloseCallback;

  @NotNull private Color myBackgroundColor;

  @Nullable private final Dimension myTextControlSize;
  @Nullable private       Rectangle myScreenBounds;
  @Nullable private       Listener  myListener;

  private boolean myEnabled = true;
  private boolean mySelected;
  private boolean myCloseButtonHovered;

  public ArrangementAtomMatchConditionComponent(@NotNull ArrangementStandardSettingsManager manager,
                                                @NotNull ArrangementColorsProvider colorsProvider,
                                                @NotNull ArrangementAtomMatchCondition condition,
                                                @Nullable Consumer<ArrangementAtomMatchConditionComponent> closeCallback)
  {
    myColorsProvider = colorsProvider;
    myCondition = condition;
    myAvailableTokens.add(condition.getType());
    myCloseCallback = closeCallback;
    ArrangementSettingsToken type = condition.getType();
    if (StdArrangementTokenType.REG_EXP.is(type)) {
      myBorderStrategy = TEXT_BORDER_STRATEGY;
    }
    else {
      myBorderStrategy = PREDEFINED_BORDER_STRATEGY;
    }
    if (type.equals(condition.getValue())) {
      myText = type.getRepresentationValue();
    }
    else if (StdArrangementTokenType.REG_EXP.is(type)) {
      myText = String.format("%s %s", type.getRepresentationValue().toLowerCase(), condition.getValue());
    }
    else {
      myText = condition.getValue().toString();
    }
    myTextControl.setTextAlign(SwingConstants.CENTER);
    myTextControl.append(myText, SimpleTextAttributes.fromTextAttributes(colorsProvider.getTextAttributes(type, false)));
    myTextControl.setOpaque(false);
    int maxWidth = manager.getWidth(type);
    if (!StdArrangementTokenType.REG_EXP.is(type) && maxWidth > 0) {
      myTextControlSize = new Dimension(maxWidth, myTextControl.getPreferredSize().height);
    }
    else {
      myTextControlSize = myTextControl.getPreferredSize();
    }

    final ArrangementRemoveConditionAction action = new ArrangementRemoveConditionAction();
    Icon buttonIcon = action.getTemplatePresentation().getIcon();
    Dimension buttonSize = new Dimension(buttonIcon.getIconWidth(), buttonIcon.getIconHeight());
    if (closeCallback == null) {
      myCloseButton = null;
      myCloseButtonBounds = null;
    }
    else {
      myCloseButton = new ActionButton(
        action,
        action.getTemplatePresentation().clone(),
        ArrangementConstants.MATCHING_RULES_CONTROL_PLACE,
        buttonSize)
      {
        @Override
        protected Icon getIcon() {
          return myCloseButtonHovered ? action.getTemplatePresentation().getHoveredIcon() : action.getTemplatePresentation().getIcon();
        }
      };
      myCloseButtonBounds = new Rectangle(0, 0, buttonIcon.getIconWidth(), buttonIcon.getIconHeight());
    }

    JPanel insetsPanel = new JPanel(new GridBagLayout()) {
      @Override
      public String toString() {
        return "insets panel for " + myText;
      }
    };

    GridBagConstraints constraints = new GridBag().anchor(GridBagConstraints.WEST).weightx(1)
      .insets(0, 0, 0, myCloseButton == null ? ArrangementConstants.BORDER_ARC_SIZE : 0);
    insetsPanel.add(myTextControl, constraints);
    insetsPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, ArrangementConstants.HORIZONTAL_PADDING, 0, 0));
    insetsPanel.setOpaque(false);

    JPanel roundBorderPanel = new JPanel(new GridBagLayout()) {
      @Override
      public void paint(Graphics g) {
        Rectangle buttonBounds = getCloseButtonScreenBounds();
        if (buttonBounds != null) {
          Point mouseScreenLocation = MouseInfo.getPointerInfo().getLocation();
          myCloseButtonHovered = buttonBounds.contains(mouseScreenLocation);
        }

        Rectangle bounds = getBounds();
        g.setColor(myBackgroundColor);
        g.fillRoundRect(0, 0, bounds.width, bounds.height, ArrangementConstants.BORDER_ARC_SIZE, ArrangementConstants.BORDER_ARC_SIZE);
        super.paint(g);
      }

      @Override
      public String toString() {
        return "round border panel for " + myText;
      }

      @Override
      protected void paintBorder(Graphics g) {
        myBorderStrategy.setup((Graphics2D)g);
        super.paintBorder(g);
      }
    };
    roundBorderPanel.add(insetsPanel, new GridBag().anchor(GridBagConstraints.WEST));
    if (myCloseButton != null) {
      roundBorderPanel.add(new InsetsPanel(myCloseButton), new GridBag().anchor(GridBagConstraints.EAST));
    }
    myBorder = myBorderStrategy.create();
    roundBorderPanel.setBorder(myBorder);
    roundBorderPanel.setOpaque(false);

    myAnimationPanel = new ArrangementAnimationPanel(roundBorderPanel, false, true) {
      @Override
      public void paint(Graphics g) {
        Point point = UIUtil.getLocationOnScreen(this);
        if (point != null) {
          Rectangle bounds = myAnimationPanel.getBounds();
          myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
        }
        if (!myEnabled && g instanceof Graphics2D) {
          ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        }
        super.paint(g);
      }
    };

    setSelected(false);
    if (myCloseButton != null) {
      myCloseButton.setVisible(false);
    }
  }

  @NotNull
  @Override
  public ArrangementAtomMatchCondition getMatchCondition() {
    return myCondition;
  }

  @Override
  public void setData(@NotNull Object data) {
    // Do nothing
  }

  @NotNull
  @Override
  public JComponent getUiComponent() {
    return myAnimationPanel;
  }

  @Nullable
  @Override
  public Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  /**
   * Instructs current component that it should {@link #getUiComponent() draw} itself according to the given 'selected' state.
   *
   * @param selected  flag that indicates if current component should be drawn as 'selected'
   */
  @Override
  public void setSelected(boolean selected) {
    boolean notifyListener = selected != mySelected;
    mySelected = selected;
    myTextControl.clear();
    TextAttributes attributes = myColorsProvider.getTextAttributes(myCondition.getType(), selected);
    myTextControl.append(myText, SimpleTextAttributes.fromTextAttributes(attributes));
    myBorder.setColor(myColorsProvider.getBorderColor(selected));
    myBackgroundColor = attributes.getBackgroundColor();
    if (notifyListener && myListener != null) {
      myListener.stateChanged();
    }
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  /**
   * Instructs current component that it should {@link #getUiComponent() draw} itself according to the given 'enabled' state.
   *
   * @param enabled  flag that indicates if current component should be drawn as 'enabled'
   */
  @Override
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
    if (!enabled) {
      setSelected(false);
    }
  }

  @Nullable
  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    Rectangle buttonBounds = getCloseButtonScreenBounds();
    if (buttonBounds == null) {
      return null;
    }
    if (myCloseButton != null && !myCloseButton.isVisible()) {
      myCloseButton.setVisible(true);
      return buttonBounds;
    }
    boolean mouseOverButton = buttonBounds.contains(event.getLocationOnScreen());
    return (mouseOverButton ^ myCloseButtonHovered) ? buttonBounds : null;
  }

  @Override
  public void onMouseRelease(@NotNull MouseEvent event) {
    Rectangle buttonBounds = getCloseButtonScreenBounds();
    if (buttonBounds != null && myCloseCallback != null && buttonBounds.contains(event.getLocationOnScreen())) {
      myCloseCallback.consume(this);
      event.consume();
    }
  }

  @Override
  public Rectangle onMouseEntered(@NotNull MouseEvent e) {
    if (myCloseButton != null) {
      myCloseButton.setVisible(true);
      return getCloseButtonScreenBounds();
    }
    return null;
  }

  @Nullable
  @Override
  public Rectangle onMouseExited() {
    if (myCloseButton == null) {
      return null;
    }
    myCloseButton.setVisible(false);
    return getCloseButtonScreenBounds();
  }

  @Nullable
  private Rectangle getCloseButtonScreenBounds() {
    if (myCloseButton == null || myScreenBounds == null) {
      return null;
    }

    Rectangle buttonBounds = SwingUtilities.convertRectangle(myCloseButton.getParent(), myCloseButtonBounds, myAnimationPanel);
    buttonBounds.x += myScreenBounds.x;
    buttonBounds.y += myScreenBounds.y;
    return buttonBounds;
  }

  @NotNull
  public ArrangementAnimationPanel getAnimationPanel() {
    return myAnimationPanel;
  }

  @Override
  public String toString() {
    return myText;
  }

  @NotNull
  @Override
  public ArrangementSettingsToken getToken() {
    return myCondition.getType();
  }

  @NotNull
  @Override
  public Set<ArrangementSettingsToken> getAvailableTokens() {
    return myAvailableTokens;
  }

  @Override
  public void chooseToken(@NotNull ArrangementSettingsToken data) throws IllegalArgumentException, UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSelected() {
    return mySelected;
  }

  @Override
  public void reset() {
    setSelected(false);
  }

  @Override
  public int getBaselineToUse(int width, int height) {
    return -1;
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public void setListener(@NotNull Listener listener) {
    myListener = listener;
  }

  private interface BorderStrategy {
    RoundedLineBorder create();
    void setup(@NotNull Graphics2D g);
  }

  private static class PredefinedConditionBorderStrategy implements BorderStrategy {
    @Override
    public RoundedLineBorder create() {
      return IdeBorderFactory.createRoundedBorder(ArrangementConstants.BORDER_ARC_SIZE);
    }

    @Override
    public void setup(@NotNull Graphics2D g) {
    }
  }

  private static class NameBorderStrategy implements BorderStrategy {

    @NotNull private final BasicStroke myStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1, new float[]{5, 5}, 0);

    @Override
    public RoundedLineBorder create() {
      return IdeBorderFactory.createRoundedBorder(ArrangementConstants.BORDER_ARC_SIZE, 2);
    }

    @Override
    public void setup(@NotNull Graphics2D g) {
      g.setStroke(myStroke);
    }
  }
}
