// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;

public class InspectionOptionsPanel extends JPanel {

  private final @Nullable OptionAccessor myOptionAccessor;
  private final GridBag myConstraints = new GridBag();
  private boolean myHasGrowing = false;

  /**
   * @deprecated Use {@link InspectionProfileEntry#getOptionsPane} to provide a declarative representation of the options.
   */
  @Deprecated
  public InspectionOptionsPanel() {
    this((OptionAccessor)null);
  }

  /**
   * @deprecated Use {@link InspectionProfileEntry#getOptionsPane} to provide a declarative representation of the options.
   */
  @Deprecated
  public InspectionOptionsPanel(@NotNull InspectionProfileEntry owner) {
    this(new OptionAccessor.Default(owner));
  }

  /**
   * @deprecated Use {@link InspectionProfileEntry#getOptionsPane} to provide a declarative representation of the options.
   */
  @Deprecated
  public InspectionOptionsPanel(@Nullable OptionAccessor optionAccessor) {
    super(new GridBagLayout());
    myConstraints
      .setDefaultAnchor(GridBagConstraints.NORTHWEST)
      .setDefaultFill(GridBagConstraints.VERTICAL)
      .setDefaultInsets(0, 0, UIUtil.DEFAULT_VGAP * 2, 0)
    ;
    myOptionAccessor = optionAccessor;
  }

  public static InspectionOptionsPanel singleCheckBox(@NotNull InspectionProfileEntry owner,
                                                      @NotNull @NlsContexts.Checkbox String label,
                                                      @Language("jvm-field-name") @NonNls String property) {
    var panel = new InspectionOptionsPanel(owner);
    panel.addCheckbox(label, property);
    return panel;
  }

  /**
   * Adds a row with a single component in the first column.
   */
  @Override
  public Component add(Component comp) {
    super.add(comp, myConstraints.nextLine());
    return comp;
  }

  /**
   * Adds a row with a single component, covering all columns but not resizing.
   */
  public void addComponent(JComponent component) {
    add(component, myConstraints.nextLine().coverLine());
  }

  /**
   * Adds a row with a single component, using as much vertical and horizontal space as possible.
   */
  public void addGrowing(Component component) {
    add(component, myConstraints.nextLine().weightx(1.0).weighty(1.0).fillCell());
    myHasGrowing = true;
  }

  /**
   * Adds a row with a single component, using as much horizontal space as possible.
   */
  public void addGrowingX(Component component) {
    add(component, myConstraints.nextLine().weightx(1.0).coverLine().fillCell());
  }

  /**
   * Adds a row with a component and its label.
   */
  public void addRow(Component label, Component component) {
    add(label, myConstraints.nextLine().next());
    add(component, myConstraints.next().insets(0, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP * 2, 0));
  }

  /**
   * Adds a row with a component and its label.
   */
  public void addLabeledRow(@NlsContexts.Label String labelText, Component component) {
    final JLabel label = new JLabel(labelText);
    label.setLabelFor(component);
    addRow(label, component);
  }

  public void addCheckbox(@NotNull @NlsContexts.Checkbox String label, @Language("jvm-field-name") @NotNull @NonNls String property) {
    addCheckboxEx(label, property);
  }

  public JCheckBox addCheckboxEx(@NotNull @NlsContexts.Checkbox String label, @Language("jvm-field-name") @NotNull @NonNls String property) {
    if (myOptionAccessor == null) {
      throw new IllegalStateException("No option accessor or owner specified in constructor call");
    }
    final boolean selected = myOptionAccessor.getOption(property);
    final JCheckBox checkBox = new JCheckBox(label, selected);
    checkBox.addItemListener(e -> myOptionAccessor.setOption(property, e.getStateChange() == ItemEvent.SELECTED));
    addComponent(checkBox);
    return checkBox;
  }

  public JCheckBox addDependentCheckBox(@NotNull @NlsContexts.Checkbox String label, @Language("jvm-field-name") @NotNull @NonNls String property,
                                        @NotNull JCheckBox controller) {
    final JCheckBox checkBox = addCheckboxEx(label, property);
    checkBox.setBorder(new EmptyBorder(new JBInsets(0, 20, 0, 0)));
    controller.addItemListener(
      e -> checkBox.setEnabled(((JCheckBox)e.getSource()).isEnabled() && e.getStateChange() == ItemEvent.SELECTED));
    checkBox.setEnabled(controller.isEnabled() && controller.isSelected());
    return checkBox;
  }

  @IntellijInternalApi
  public void addGlueIfNeeded() {
    if (!myHasGrowing) {
      myHasGrowing = true;
      add(new Spacer(), myConstraints.nextLine().weightx(1.0).weighty(1.0).fillCell().coverLine().insets(0, 0, 0, 0));
    }
  }

  /**
   * Returns the minimum size for lists in inspection options.
   */
  public static @NotNull Dimension getMinimumListSize() {
    return JBUI.size(150, 100);
  }

  /**
   * Returns the minimum size for lists to show at least 3 button controls in their toolbar.
   */
  public static @NotNull Dimension getMinimumLongListSize() {
    return JBUI.size(150, 120);
  }
}
