// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Predicate;

public class TextComponentEmptyText extends StatusText {
  /**
   * Client property to determine visibility of the status text.
   * <p>
   * Expecting an instance of {@link Predicate}&lt;{@link JTextComponent}&gt; like:
   * <p>
   * {@code jbTextField.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JBTextField>)f -> StringUtil.isEmpty(f.getText()));}
   */
  public static final String STATUS_VISIBLE_FUNCTION = "StatusVisibleFunction";

  private final JTextComponent myOwner;
  private final boolean myDynamicStatus;
  private String myStatusTriggerText = "";

  TextComponentEmptyText(JTextComponent owner, boolean dynamicStatus) {
    super(owner);
    myOwner = owner;
    myDynamicStatus = dynamicStatus;
    clear();
    myOwner.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myOwner.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        myOwner.repaint();
      }
    });
  }

  public void setTextToTriggerStatus(@NotNull String defaultText) {
    myStatusTriggerText = defaultText;
  }

  public @NotNull String getStatusTriggerText() {
    return myStatusTriggerText;
  }

  public void paintStatusText(Graphics g) {
    if (!isFontSet()) {
      setFont(myOwner.getFont());
    }
    paint(myOwner, g);
  }

  public void resetFontToOwnerFont() {
    setFont(myOwner.getFont());
  }

  @Override
  @SuppressWarnings({"unchecked"})
  protected boolean isStatusVisible() {
    if (myDynamicStatus) {
      Object function = myOwner.getClientProperty(STATUS_VISIBLE_FUNCTION);
      if (function instanceof Predicate) {
        return ((Predicate<JTextComponent>)function).test(myOwner);
      }
    }

    return myOwner.getText().equals(myStatusTriggerText) && !myOwner.isFocusOwner();
  }

  @Override
  protected Rectangle getTextComponentBound() {
    Rectangle b = myOwner.getBounds();
    Insets insets = ObjectUtils.notNull(myOwner.getInsets(), JBInsets.emptyInsets());
    Insets margin = ObjectUtils.notNull(myOwner.getMargin(), JBInsets.emptyInsets());
    Insets ipad = getComponent().getIpad();
    int left = insets.left + margin.left - ipad.left;
    int right = insets.right + margin.right - ipad.right;
    int top = insets.top + margin.top - ipad.top;
    int bottom = insets.bottom + margin.bottom - ipad.bottom;
    return new Rectangle(left, top,
                         b.width - left - right,
                         b.height - top - bottom);
  }

  @Override
  protected @NotNull Rectangle adjustComponentBounds(@NotNull JComponent component, @NotNull Rectangle bounds) {
    Dimension size = component.getPreferredSize();
    int width = Math.min(size.width, bounds.width);

    return component == getComponent()
           ? new Rectangle(bounds.x, bounds.y, width, bounds.height)
           : new Rectangle(bounds.x + bounds.width - width, bounds.y, width, bounds.height);
  }

  /**
   * Show placeholder when the text component is empty.
   * @param textComponent target text component.
   */
  public static void setupPlaceholderVisibility(@NotNull JTextComponent textComponent) {
    textComponent.putClientProperty(STATUS_VISIBLE_FUNCTION, (Predicate<JTextComponent>)f -> StringUtil.isEmpty(f.getText()));
  }
}
