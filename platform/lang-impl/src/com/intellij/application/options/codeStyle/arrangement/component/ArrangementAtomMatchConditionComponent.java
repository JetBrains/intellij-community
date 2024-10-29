// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.action.ArrangementRemoveConditionAction;
import com.intellij.application.options.codeStyle.arrangement.animation.ArrangementAnimationPanel;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.util.InsetsPanel;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link ArrangementUiComponent} for {@link ArrangementAtomMatchCondition} representation.
 * <p/>
 * Not thread-safe.
 */
public final class ArrangementAtomMatchConditionComponent implements ArrangementUiComponent {

  private static final @NotNull BorderStrategy TEXT_BORDER_STRATEGY       = new NameBorderStrategy();
  private static final @NotNull BorderStrategy PREDEFINED_BORDER_STRATEGY = new PredefinedConditionBorderStrategy();
  private final @NotNull Set<ArrangementSettingsToken> myAvailableTokens = new HashSet<>();
  private final @NotNull BorderStrategy myBorderStrategy;
  private final @NotNull @Nls String myText;
  private final @NotNull ArrangementColorsProvider myColorsProvider;
  private final @NotNull RoundedLineBorder myBorder;
  private final @NotNull ArrangementAtomMatchCondition myCondition;
  private final @NotNull ArrangementAnimationPanel myAnimationPanel;
  private final @Nullable ActionButton myCloseButton;
  private final @Nullable Rectangle myCloseButtonBounds;
  private final @Nullable Consumer<? super ArrangementAtomMatchConditionComponent> myCloseCallback;
  private final @Nullable Dimension myTextControlSize;
  private final @NotNull SimpleColoredComponent myTextControl = new SimpleColoredComponent() {
    @Override
    public @NotNull Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
      return myTextControlSize == null ? super.getPreferredSize() : myTextControlSize;
    }

    @Override
    public String toString() {
      return "text component for " + myText;
    }
  };
  private @NotNull Color myBackgroundColor;
  private @Nullable Rectangle myScreenBounds;
  private @Nullable Listener myListener;

  private boolean myInverted = false;
  private boolean myEnabled = true;
  private boolean mySelected;
  private boolean myCloseButtonHovered;

  // cached value for inverted atom condition, e.g. condition: 'static', opposite: 'not static'
  private @Nullable ArrangementAtomMatchCondition myOppositeCondition;
  private @Nullable @Nls String myInvertedText;

  public ArrangementAtomMatchConditionComponent(@NotNull ArrangementStandardSettingsManager manager,
                                                @NotNull ArrangementColorsProvider colorsProvider,
                                                @NotNull ArrangementAtomMatchCondition condition,
                                                @Nullable Consumer<? super ArrangementAtomMatchConditionComponent> closeCallback)
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
    if (type.equals(condition.getValue()) || condition.getValue() instanceof Boolean) {
      myText = type.getRepresentationValue();
    }
    else if (StdArrangementTokenType.REG_EXP.is(type)) {
      myText = StringUtil.toLowerCase(type.getRepresentationValue()) + " " + condition.getValue();
    }
    else {
      myText = condition.getPresentableValue();
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
    if (closeCallback == null) {
      myCloseButton = null;
      myCloseButtonBounds = null;
    }
    else {
      myCloseButton = new ActionButton(
        action,
        action.getTemplatePresentation().clone(),
        ArrangementConstants.MATCHING_RULES_CONTROL_PLACE,
        JBUI.emptySize())
      {
        @Override
        public Icon getIcon() {
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
    insetsPanel.setBorder(JBUI.Borders.emptyLeft(ArrangementConstants.HORIZONTAL_PADDING));
    insetsPanel.setOpaque(false);

    JPanel roundBorderPanel = new JPanel(new GridBagLayout()) {
      @Override
      public void paint(Graphics g) {
        Rectangle buttonBounds = getCloseButtonScreenBounds();
        if (buttonBounds != null) {
          final PointerInfo info = MouseInfo.getPointerInfo();
          myCloseButtonHovered = info != null && buttonBounds.contains(info.getLocation());
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
    setData(myCondition.getValue());
  }

  @Override
  public @NotNull ArrangementAtomMatchCondition getMatchCondition() {
    if (Boolean.valueOf(myInverted).equals(myCondition.getValue())) {
      if (myOppositeCondition == null) {
        myOppositeCondition = new ArrangementAtomMatchCondition(myCondition.getType(), !myInverted);
      }
      return myOppositeCondition;
    }
    return myCondition;
  }

  @Override
  public void setData(@NotNull Object data) {
    if (data instanceof Boolean && myCondition.getType() instanceof InvertibleArrangementSettingsToken) {
      myInverted = !((Boolean)data);
      updateComponentText(mySelected);
    }
  }

  @Override
  public @NotNull JComponent getUiComponent() {
    return myAnimationPanel;
  }

  @Override
  public @Nullable Rectangle getScreenBounds() {
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
    TextAttributes attributes = updateComponentText(selected);
    myBorder.setColor(myColorsProvider.getBorderColor(selected));
    myBackgroundColor = attributes.getBackgroundColor();
    if (notifyListener && myListener != null) {
      myListener.stateChanged();
    }
  }

  private @NotNull TextAttributes updateComponentText(boolean selected) {
    myTextControl.clear();
    TextAttributes attributes = myColorsProvider.getTextAttributes(myCondition.getType(), selected);
    myTextControl.append(getComponentText(), SimpleTextAttributes.fromTextAttributes(attributes));
    return attributes;
  }

  private @Nls String getComponentText() {
    if (myInverted) {
      if (StringUtil.isEmpty(myInvertedText)) {
        final ArrangementSettingsToken token = myCondition.getType();
        assert token instanceof InvertibleArrangementSettingsToken;
        myInvertedText = ((InvertibleArrangementSettingsToken)token).getInvertedRepresentationValue();
      }
      return myInvertedText;
    }
    return myText;
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

  @Override
  public @Nullable Rectangle onMouseMove(@NotNull MouseEvent event) {
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

  @Override
  public @Nullable Rectangle onMouseExited() {
    if (myCloseButton == null) {
      return null;
    }
    myCloseButton.setVisible(false);
    return getCloseButtonScreenBounds();
  }

  private @Nullable Rectangle getCloseButtonScreenBounds() {
    if (myCloseButton == null || myScreenBounds == null) {
      return null;
    }

    Rectangle buttonBounds = SwingUtilities.convertRectangle(myCloseButton.getParent(), myCloseButtonBounds, myAnimationPanel);
    buttonBounds.x += myScreenBounds.x;
    buttonBounds.y += myScreenBounds.y;
    return buttonBounds;
  }

  public @NotNull ArrangementAnimationPanel getAnimationPanel() {
    return myAnimationPanel;
  }

  @Override
  public String toString() {
    return getComponentText();
  }

  @Override
  public @NotNull ArrangementSettingsToken getToken() {
    return myCondition.getType();
  }

  @Override
  public @NotNull Set<ArrangementSettingsToken> getAvailableTokens() {
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
    setData(true);
  }

  @Override
  public int getBaselineToUse(int width, int height) {
    return -1;
  }

  @Override
  public void setListener(@NotNull Listener listener) {
    myListener = listener;
  }

  @Override
  public void handleMouseClickOnSelected() {
    if (myInverted || !(myCondition.getType() instanceof InvertibleArrangementSettingsToken)) {
      setSelected(false);
    }
    setData(myInverted);
  }

  @Override
  public boolean alwaysCanBeActive() {
    return myInverted;
  }

  private interface BorderStrategy {
    RoundedLineBorder create();
    void setup(@NotNull Graphics2D g);
  }

  private static final class PredefinedConditionBorderStrategy implements BorderStrategy {
    @Override
    public RoundedLineBorder create() {
      return IdeBorderFactory.createRoundedBorder(ArrangementConstants.BORDER_ARC_SIZE);
    }

    @Override
    public void setup(@NotNull Graphics2D g) {
    }
  }

  private static final class NameBorderStrategy implements BorderStrategy {

    private static final @NotNull BasicStroke
      STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1, new float[]{5, 5}, 0);

    @Override
    public RoundedLineBorder create() {
      return IdeBorderFactory.createRoundedBorder(ArrangementConstants.BORDER_ARC_SIZE, 2);
    }

    @Override
    public void setup(@NotNull Graphics2D g) {
      g.setStroke(STROKE);
    }
  }
}
