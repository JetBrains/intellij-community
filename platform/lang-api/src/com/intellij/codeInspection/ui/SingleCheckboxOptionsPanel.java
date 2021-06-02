/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SingleCheckboxOptionsPanel extends InspectionOptionsPanel {

    public SingleCheckboxOptionsPanel(@NotNull @NlsContexts.Checkbox String label,
                                      @NotNull InspectionProfileEntry owner,
                                      @NonNls String property) {
        final boolean selected = getPropertyValue(owner, property);
        final JCheckBox checkBox = new JCheckBox(label, selected);
        final ButtonModel model = checkBox.getModel();
        final SingleCheckboxChangeListener listener =
                new SingleCheckboxChangeListener(owner, property, model);
        model.addChangeListener(listener);
        add(checkBox);
    }

    private static boolean getPropertyValue(InspectionProfileEntry owner,
                                            String property) {
      return ReflectionUtil.getField(owner.getClass(), owner, boolean.class, property);
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
          ReflectionUtil.setField(owner.getClass(), owner, boolean.class, property, selected);
        }
    }
}