// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class TitledSeparator extends JPanel {
  public static final int TOP_INSET = 7;
  public static final int BOTTOM_INSET = 5;
  public static final int SEPARATOR_LEFT_INSET = 6;
  public static final int SEPARATOR_RIGHT_INSET = 0;
  private static final int SEPARATOR_TOP_INSET = 2;

  private static final Color ENABLED_SEPARATOR_FOREGROUND = JBColor.namedColor("Group.separatorColor", new JBColor(Gray.xCD, Gray.x51));
  private static final Color DISABLED_SEPARATOR_FOREGROUND = JBColor.namedColor("Group.disabledSeparatorColor", ENABLED_SEPARATOR_FOREGROUND);

  private FocusListener labelFocusListener;

  @NotNull
  public static Border createEmptyBorder() {
    return JBUI.Borders.empty(TOP_INSET, 0, BOTTOM_INSET, 0);
  }

  protected final JBLabel myLabel = new JBLabel();
  protected final JSeparator mySeparator = new JSeparator(SwingConstants.HORIZONTAL);
  private @NlsContexts.Separator String originalText;

  public TitledSeparator() {
    this("");
  }

  public TitledSeparator(@NlsContexts.Separator String text) {
    this(text, null);
  }

  public TitledSeparator(@NlsContexts.Separator String text, @Nullable JComponent labelFor) {
    mySeparator.setForeground(ENABLED_SEPARATOR_FOREGROUND);

    setLayout(new GridBagLayout());
    addComponents(false);
    setText(text);
    setLabelFor(labelFor);
    setOpaque(false);
    updateLabelFont();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    updateLabelFont();
  }

  public void setLabelFocusable(boolean focusable) {
    myLabel.setFocusable(focusable);

    remove(myLabel);
    remove(mySeparator);
    addComponents(focusable);

    if (focusable) {
      if (labelFocusListener == null) {
        labelFocusListener = new FocusListener() {
          @Override
          public void focusGained(FocusEvent e) {
            setLabelBorder(true);
          }

          @Override
          public void focusLost(FocusEvent e) {
            setLabelBorder(false);
          }
        };
        myLabel.addFocusListener(labelFocusListener);

        setLabelBorder(false);
      }
    }
    else {
      if (labelFocusListener != null) {
        myLabel.removeFocusListener(labelFocusListener);
        labelFocusListener = null;
      }
      myLabel.setBorder(null);
    }
  }

  private void setLabelBorder(boolean focused) {
    myLabel.setBorder(TitleSelectionBorder.getLabelBorder(focused));
  }

  private void addComponents(boolean focusable) {
    if (focusable) {
      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
      add(mySeparator,
          new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                 JBUI.insets(TOP_INSET + SEPARATOR_TOP_INSET / 2, 0, BOTTOM_INSET - SEPARATOR_TOP_INSET / 2, SEPARATOR_RIGHT_INSET),
                                 0, 0));
      setBorder(null);
    }
    else {
      add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
      add(mySeparator,
          new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                 JBUI.insets(SEPARATOR_TOP_INSET, SEPARATOR_LEFT_INSET, 0, SEPARATOR_RIGHT_INSET), 0, 0));
      setBorder(createEmptyBorder());
    }
  }

  private void updateLabelFont() {
    if (myLabel != null) {
      Font labelFont = StartupUiUtil.getLabelFont();
      myLabel.setFont(RelativeFont.NORMAL.fromResource("TitledSeparator.fontSizeOffset", 0).derive(labelFont));
    }
  }

  public @NlsContexts.Separator String getText() {
    return originalText;
  }

  public void setText(@NlsContexts.Separator String text) {
    originalText = text;
    myLabel.setText(text != null && text.startsWith("<html>") ? text : UIUtil.replaceMnemonicAmpersand(originalText));
  }
  public void setTitleFont(Font font) {
    myLabel.setFont(font);
  }

  public Font getTitleFont() {
    return myLabel.getFont();
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public JSeparator getSeparator() {
    return mySeparator;
  }


  public Component getLabelFor() {
    return myLabel.getLabelFor();
  }

  public void setLabelFor(Component labelFor) {
    myLabel.setLabelFor(labelFor);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myLabel.setEnabled(enabled);
    mySeparator.setEnabled(enabled);

    mySeparator.setForeground(enabled ? ENABLED_SEPARATOR_FOREGROUND : DISABLED_SEPARATOR_FOREGROUND);
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = super.getAccessibleContext();
      accessibleContext.setAccessibleName(myLabel.getText());
    }
    return accessibleContext;
  }

  private static class TitleSelectionBorder {

    private static final int INSIDE_BOTTOM_RIGHT_INSET = 2;
    private static final int FOCUS_THICKNESS = 1;

    private static Border getLabelBorder(boolean focused) {
      if (!focused) {
        return new EmptyBorder(getInsets());
      }

      int arcSize = JBUIScale.scale(Registry.intValue("ide.link.button.focus.round.arc", 4));
      return new CompoundBorder(new EmptyBorder(getOutsideFrameInsets()), new CompoundBorder(
        new RoundedLineBorder(JBUI.CurrentTheme.Link.FOCUSED_BORDER_COLOR, arcSize, FOCUS_THICKNESS), new EmptyBorder(getInsideFrameInsets())));
    }

    private static void add(Insets destInsets, Insets insetsToAdd) {
      destInsets.top += insetsToAdd.top;
      destInsets.bottom += insetsToAdd.bottom;
      destInsets.left += insetsToAdd.left;
      destInsets.right += insetsToAdd.right;
    }

    /**
     * Insets between focus frame and label
     */
    private static Insets getInsideFrameInsets() {
      return new JBInsets(0, 0, INSIDE_BOTTOM_RIGHT_INSET, INSIDE_BOTTOM_RIGHT_INSET);
    }

    /**
     * Focus frame sizes
     */
    private static Insets getFrameInsets() {
      // RoundedLineBorder doesn't use scale
      //noinspection UseDPIAwareInsets
      return new Insets(FOCUS_THICKNESS, FOCUS_THICKNESS, FOCUS_THICKNESS, FOCUS_THICKNESS);
    }

    /**
     * Insets outside focus frame
     */
    private static Insets getOutsideFrameInsets() {
      Insets insets = getInsideFrameInsets();
      add(insets, getFrameInsets());

      //noinspection UseDPIAwareInsets
      return new Insets(JBUIScale.scale(TOP_INSET) - insets.top,
                        insets.left + 1, // RoundedLineBorder is a little clipped near edges, reserve additional space
                        JBUIScale.scale(BOTTOM_INSET) - insets.bottom, SEPARATOR_LEFT_INSET - insets.right);
    }

    private static Insets getInsets() {
      Insets result = getInsideFrameInsets();
      add(result, getFrameInsets());
      add(result, getOutsideFrameInsets());
      return result;
    }
  }
}
