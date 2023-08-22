// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static java.awt.GridBagConstraints.*;

/**
 * also consider using {@link UI.PanelFactory} for non-trivial forms
 */
public class FormBuilder {
  private boolean myAlignLabelOnRight;

  private int myLineCount = 0;
  private final JPanel myPanel;
  private boolean myVertical;

  private int myVerticalGap;
  private int myHorizontalGap;
  private int myFormLeftIndent;

  public FormBuilder() {
    myPanel = new JPanel(new GridBagLayout());
    myVertical = false;
    myAlignLabelOnRight = false;
    myVerticalGap = DEFAULT_VGAP;
    myHorizontalGap = DEFAULT_HGAP;
    myFormLeftIndent = 0;
  }

  public static FormBuilder createFormBuilder() {
    return new FormBuilder();
  }

  public FormBuilder addLabeledComponent(@Nullable JComponent label, @NotNull JComponent component) {
    return addLabeledComponent(label, component, myVerticalGap, false);
  }

  public FormBuilder addLabeledComponent(@Nullable JComponent label, @NotNull JComponent component, final int topInset) {
    return addLabeledComponent(label, component, topInset, false);
  }

  public FormBuilder addLabeledComponent(@Nullable JComponent label, @NotNull JComponent component, boolean labelOnTop) {
    return addLabeledComponent(label, component, myVerticalGap, labelOnTop);
  }

  public FormBuilder addLabeledComponent(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component) {
    return addLabeledComponent(labelText, component, myVerticalGap, false);
  }

  public FormBuilder addLabeledComponent(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component, final int topInset) {
    return addLabeledComponent(labelText, component, topInset, false);
  }

  public FormBuilder addLabeledComponent(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component, boolean labelOnTop) {
    return addLabeledComponent(labelText, component, myVerticalGap, labelOnTop);
  }

  public FormBuilder addLabeledComponent(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component, final int topInset, boolean labelOnTop) {
    JLabel label = createLabelForComponent(labelText, component);
    return addLabeledComponent(label, component, topInset, labelOnTop);
  }

  @NotNull
  private static JLabel createLabelForComponent(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component) {
    JLabel label = new JLabel(UIUtil.replaceMnemonicAmpersand(labelText));
    label.setLabelFor(component);
    return label;
  }

  public FormBuilder addComponent(@NotNull JComponent component) {
    return addLabeledComponent((JLabel)null, component, myVerticalGap, false);
  }

  public FormBuilder addComponent(@NotNull JComponent component, final int topInset) {
    return addLabeledComponent((JLabel)null, component, topInset, false);
  }

  @NotNull
  public FormBuilder addComponentFillVertically(@NotNull JComponent component, int topInset) {
    return addLabeledComponent(null, component, topInset, false, true);
  }

  public FormBuilder addSeparator(final int topInset) {
    return addComponent(new JSeparator(), topInset);
  }

  public FormBuilder addSeparator() {
    return addSeparator(myVerticalGap);
  }

  public FormBuilder addVerticalGap(final int height) {
    if (height == -1) {
      myPanel.add(new JLabel(), new GridBagConstraints(0, myLineCount++, 2, 1, 0, 1, CENTER, NONE, JBInsets.emptyInsets(), 0, 0));
      return this;
    }

    return addLabeledComponent((JLabel)null,
                               new Box.Filler(new JBDimension(0, height), new JBDimension(0, height), new JBDimension(Short.MAX_VALUE, height)));
  }

  public FormBuilder addTooltip(final @NlsContexts.Label String text) {
    final JBLabel label = new JBLabel(text, UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER);
    label.setBorder(JBUI.Borders.emptyLeft(10));
    return addComponentToRightColumn(label, 1);
  }

  public FormBuilder addComponentToRightColumn(@NotNull final JComponent component) {
    return addComponentToRightColumn(component, myVerticalGap);
  }

  public FormBuilder addComponentToRightColumn(@NotNull final JComponent component, final int topInset) {
    return addLabeledComponent(new JLabel(), component, topInset);
  }

  public FormBuilder addLabeledComponent(@Nullable JComponent label,
                                         @NotNull JComponent component,
                                         int topInset,
                                         boolean labelOnTop) {
    boolean fillVertically = component instanceof JScrollPane;
    return addLabeledComponent(label, component, topInset, labelOnTop, fillVertically);
  }

  public FormBuilder addLabeledComponentFillVertically(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component) {
    JLabel label = createLabelForComponent(labelText, component);
    return addLabeledComponent(label, component, myVerticalGap, true, true);
  }

  private FormBuilder addLabeledComponent(@Nullable JComponent label, @NotNull JComponent component, int topInset, boolean labelOnTop, boolean fillVertically) {
    GridBagConstraints c = new GridBagConstraints();
    topInset = myLineCount > 0 ? topInset : 0;

    if (myVertical || labelOnTop || label == null) {
      c.gridwidth = 2;
      c.gridx = 0;
      c.gridy = myLineCount;
      c.weightx = 0;
      c.weighty = 0;
      c.fill = NONE;
      c.anchor = getLabelAnchor(false, fillVertically);
      c.insets = JBUI.insets(topInset, myFormLeftIndent, DEFAULT_VGAP, 0);

      if (label != null) myPanel.add(label, c);

      c.gridx = 0;
      c.gridy = myLineCount + 1;
      c.weightx = 1.0;
      c.weighty = getWeightY(fillVertically);
      c.fill = getFill(component, fillVertically);
      c.anchor = WEST;
      c.insets = JBUI.insets(label == null ? topInset : 0, myFormLeftIndent, 0, 0);

      myPanel.add(component, c);

      myLineCount += 2;
    }
    else {
      c.gridwidth = 1;
      c.gridx = 0;
      c.gridy = myLineCount;
      c.weightx = 0;
      c.weighty = 0;
      c.fill = NONE;
      c.anchor = getLabelAnchor(true, fillVertically);
      c.insets = JBUI.insets(topInset, myFormLeftIndent, 0, myHorizontalGap);

      myPanel.add(label, c);

      c.gridx = 1;
      c.weightx = 1;
      c.weighty = getWeightY(fillVertically);
      c.fill = getFill(component, fillVertically);
      c.anchor = WEST;
      c.insets = JBUI.insets(topInset, 0, 0, 0);

      myPanel.add(component, c);

      myLineCount++;
    }

    return this;
  }

  private int getLabelAnchor(boolean honorAlignment, boolean fillVertically) {
    if (fillVertically) return honorAlignment && myAlignLabelOnRight ? NORTHEAST : NORTHWEST;
    return honorAlignment && myAlignLabelOnRight ? EAST : WEST;
  }

  protected int getFill(JComponent component) {
    if (component instanceof JComboBox ||
        component instanceof JSpinner ||
        component instanceof JButton ||
        component instanceof JTextField && ((JTextField)component).getColumns() != 0) {
      return NONE;
    }
    return HORIZONTAL;
  }

  private int getFill(JComponent component, boolean fillVertically) {
    if (fillVertically) {
      return BOTH;
    }
    return getFill(component);
  }

  private static int getWeightY(boolean fillVertically) {
    return fillVertically ? 1 : 0;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public int getLineCount() {
    return myLineCount;
  }

  public FormBuilder setAlignLabelOnRight(boolean alignLabelOnRight) {
    myAlignLabelOnRight = alignLabelOnRight;
    return this;
  }

  public FormBuilder setVertical(boolean vertical) {
    myVertical = vertical;
    return this;
  }

  public FormBuilder setVerticalGap(int verticalGap) {
    myVerticalGap = verticalGap;
    return this;
  }

  public FormBuilder setHorizontalGap(int horizontalGap) {
    myHorizontalGap = horizontalGap;
    return this;
  }

  public FormBuilder setFormLeftIndent(int formLeftIndent) {
    myFormLeftIndent = formLeftIndent;
    return this;
  }
}
