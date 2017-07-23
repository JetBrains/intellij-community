/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

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
        configureCheckbox(myOptionAccessor, property, checkBox);
        addComponent(checkBox);
        return checkBox;
    }

    public JCheckBox addDependentCheckBox(String label, @NonNls String property, JCheckBox controller) {
        final JCheckBox checkBox = addCheckboxEx(label, property);
        checkBox.setBorder(new EmptyBorder(new JBInsets(0, 30, 0, 0)));
        controller.addChangeListener(e -> {
            checkBox.setEnabled(controller.isEnabled() && controller.isSelected());
        });
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

    private static void configureCheckbox(OptionAccessor accessor, String property, JCheckBox checkBox) {
        final ButtonModel model = checkBox.getModel();
        final CheckboxChangeListener changeListener = new CheckboxChangeListener(accessor, property, model);
        model.addChangeListener(changeListener);
    }

    private static class CheckboxChangeListener implements ChangeListener {
        private final OptionAccessor myAccessor;
        private final String property;
        private final ButtonModel model;

        CheckboxChangeListener(OptionAccessor myAccessor, String property, ButtonModel model) {
            this.myAccessor = myAccessor;
            this.property = property;
            this.model = model;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            myAccessor.setOption(property, model.isSelected());
        }
    }
}