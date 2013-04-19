/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.lang.reflect.Field;

public class SingleCheckboxOptionsPanel extends JPanel {

    public SingleCheckboxOptionsPanel(@NotNull String label,
                                      @NotNull InspectionProfileEntry owner,
                                      @NonNls String property) {
        super(new GridBagLayout());
        final boolean selected = getPropertyValue(owner, property);
        final JCheckBox checkBox = new JCheckBox(label, selected);
        final ButtonModel model = checkBox.getModel();
        final SingleCheckboxChangeListener listener =
                new SingleCheckboxChangeListener(owner, property, model);
        model.addChangeListener(listener);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(checkBox, constraints);
    }

    private static boolean getPropertyValue(InspectionProfileEntry owner,
                                            String property) {
        try {
            final Class<? extends InspectionProfileEntry> aClass = owner.getClass();
            final Field field = aClass.getField(property);
            return field.getBoolean(owner);
        } catch (IllegalAccessException ignore) {
            return false;
        } catch (NoSuchFieldException ignore) {
            return false;
        }

    }

    private static class SingleCheckboxChangeListener
            implements ChangeListener {

        private final InspectionProfileEntry owner;
        private final String property;
        private final ButtonModel model;

        SingleCheckboxChangeListener(InspectionProfileEntry owner,
                                     String property, ButtonModel model) {
            this.owner = owner;
            this.property = property;
            this.model = model;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            setPropertyValue(owner, property, model.isSelected());
        }

        private static void setPropertyValue(InspectionProfileEntry owner,
                                             String property,
                                             boolean selected) {
            try {
                final Class<? extends InspectionProfileEntry> aClass =
                        owner.getClass();
                final Field field = aClass.getField(property);
                field.setBoolean(owner, selected);
            } catch (IllegalAccessException ignore) {
                // do nothing
            } catch (NoSuchFieldException ignore) {
                // do nothing
            }
        }
    }
}