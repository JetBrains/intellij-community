// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * @author Bas Leijdekkers
 */
public class MultipleCheckboxOptionsPanel extends JPanel {

    private final OptionAccessor myOptionAccessor;

    public MultipleCheckboxOptionsPanel(final InspectionProfileEntry owner) {
        this(new OptionAccessor.Default(owner));
    }

    public MultipleCheckboxOptionsPanel(final OptionAccessor optionAccessor) {
        super(new GridBagLayout());
        myOptionAccessor = optionAccessor;
    }

    public void addCheckbox(String label, @NonNls String property) {
        addCheckboxEx(label, property);
    }

    public JCheckBox addCheckboxEx(String label, @NonNls String property) {
        final boolean selected = myOptionAccessor.getOption(property);
        final JCheckBox checkBox = new JCheckBox(label, selected);
        checkBox.addItemListener(e -> myOptionAccessor.setOption(property, e.getStateChange() == ItemEvent.SELECTED));
        addComponent(checkBox);
        return checkBox;
    }

    public JCheckBox addDependentCheckBox(String label, @NonNls String property, JCheckBox controller) {
        final JCheckBox checkBox = addCheckboxEx(label, property);
        checkBox.setBorder(new EmptyBorder(new JBInsets(0, 30, 0, 0)));
        controller.addItemListener(
          e -> checkBox.setEnabled(((JCheckBox)e.getSource()).isEnabled() && e.getStateChange() == ItemEvent.SELECTED));
        checkBox.setEnabled(controller.isEnabled() && controller.isSelected());
        return checkBox;
    }

    public void addComponent(JComponent component) {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        final Component[] components = getComponents();
        removeAll();
        for (Component component1 : components) {
            add(component1, constraints);
            constraints.gridy++;
        }
        constraints.weighty = 1.0;
        add(component, constraints);
    }
}