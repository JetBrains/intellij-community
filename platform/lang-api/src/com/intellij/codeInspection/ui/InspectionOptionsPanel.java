// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.HashMap;
import java.util.Map;

public class InspectionOptionsPanel extends JPanel implements InspectionOptionContainer {

  @Nullable
  private final OptionAccessor myOptionAccessor;
  private final @NotNull Map<@NonNls String, @NlsContexts.Checkbox String> myCheckBoxLabels = new HashMap<>();

  public InspectionOptionsPanel() {
    this((OptionAccessor)null);
  }

  public InspectionOptionsPanel(@NotNull InspectionProfileEntry owner) {
    this(new OptionAccessor.Default(owner));
  }

  public InspectionOptionsPanel(@Nullable OptionAccessor optionAccessor) {
    super(new MigLayout("fillx, ins 0"));
    myOptionAccessor = optionAccessor;
  }

  public static InspectionOptionsPanel singleCheckBox(@NotNull InspectionProfileEntry owner,
                                                      @NotNull @NlsContexts.Checkbox String label,
                                                      @NonNls String property) {
    var panel = new InspectionOptionsPanel(owner);
    panel.addCheckbox(label, property);
    return panel;
  }

  public void addRow(Component label, Component component) {
    add(label, "");
    add(component, "pushx, wrap");
  }

  public void addLabeledRow(@NlsContexts.Label String labelText, Component component) {
    final JLabel label = new JLabel(labelText);
    label.setLabelFor(component);
    addRow(label, component);
  }

  /**
   * Adds a row with a single component, using as much vertical and horizontal space as possible.
   */
  public void addGrowing(Component component) {
    add(component, "push, grow, wrap");
  }

  @Override
  public Component add(Component comp) {
    super.add(comp, "span, wrap");
    return comp;
  }

  public void addCheckbox(@NotNull @NlsContexts.Checkbox String label, @NotNull @NonNls String property) {
    addCheckboxEx(label, property);
  }

  public JCheckBox addCheckboxEx(@NotNull @NlsContexts.Checkbox String label, @NotNull @NonNls String property) {
    if (myOptionAccessor == null) {
      throw new IllegalStateException("No option accessor or owner specified in constructor call");
    }
    final boolean selected = myOptionAccessor.getOption(property);
    final JCheckBox checkBox = new JCheckBox(label, selected);
    checkBox.addItemListener(e -> myOptionAccessor.setOption(property, e.getStateChange() == ItemEvent.SELECTED));
    addComponent(checkBox);
    myCheckBoxLabels.put(property, label);
    return checkBox;
  }

  @Override
  public @NotNull HtmlChunk getLabelForCheckbox(@NotNull @NonNls String property) {
    String label = myCheckBoxLabels.get(property);
    if (label == null) {
      throw new IllegalArgumentException("Invalid property name: " + property);
    }
    return HtmlChunk.text(label);
  }

  public JCheckBox addDependentCheckBox(@NotNull @NlsContexts.Checkbox String label, @NotNull @NonNls String property,
                                        @NotNull JCheckBox controller) {
    final JCheckBox checkBox = addCheckboxEx(label, property);
    checkBox.setBorder(new EmptyBorder(new JBInsets(0, 20, 0, 0)));
    controller.addItemListener(
      e -> checkBox.setEnabled(((JCheckBox)e.getSource()).isEnabled() && e.getStateChange() == ItemEvent.SELECTED));
    checkBox.setEnabled(controller.isEnabled() && controller.isSelected());
    return checkBox;
  }

  public void addComponent(JComponent component) {
    add(component, "span, wrap, grow");
  }

  static public @NotNull Dimension getMinimumListSize() {
    return JBUI.size(150, 100);
  }
}
