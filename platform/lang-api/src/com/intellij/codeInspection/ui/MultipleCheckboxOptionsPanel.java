// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.event.ItemEvent;

/**
 * @author Bas Leijdekkers
 */
public class MultipleCheckboxOptionsPanel extends InspectionOptionsPanel {

    private final OptionAccessor myOptionAccessor;

    public MultipleCheckboxOptionsPanel(final InspectionProfileEntry owner) {
        this(new OptionAccessor.Default(owner));
    }

    public MultipleCheckboxOptionsPanel(final OptionAccessor optionAccessor) {
        myOptionAccessor = optionAccessor;
    }

    public void addCheckbox(@NlsContexts.Checkbox String label, @NonNls String property) {
        addCheckboxEx(label, property);
    }

    public JCheckBox addCheckboxEx(@NlsContexts.Checkbox String label, @NonNls String property) {
        final boolean selected = myOptionAccessor.getOption(property);
        final JCheckBox checkBox = new JCheckBox(label, selected);
        checkBox.addItemListener(e -> myOptionAccessor.setOption(property, e.getStateChange() == ItemEvent.SELECTED));
        addComponent(checkBox);
        return checkBox;
    }

    public JCheckBox addDependentCheckBox(@NlsContexts.Checkbox String label, @NonNls String property, JCheckBox controller) {
        final JCheckBox checkBox = addCheckboxEx(label, property);
        checkBox.setBorder(new EmptyBorder(new JBInsets(0, 20, 0, 0)));
        controller.addItemListener(
          e -> checkBox.setEnabled(((JCheckBox)e.getSource()).isEnabled() && e.getStateChange() == ItemEvent.SELECTED));
        checkBox.setEnabled(controller.isEnabled() && controller.isSelected());
        return checkBox;
    }

    public void addComponent(JComponent component) {
        add(component);
    }
}