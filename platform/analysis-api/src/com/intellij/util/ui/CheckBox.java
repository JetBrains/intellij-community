/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.intellij.util.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class CheckBox extends JCheckBox {

  /**
   * @param property field must be non-private (or ensure that it won't be scrambled in other means)
   */
    public CheckBox(@NotNull String label,
                    @NotNull InspectionProfileEntry owner,
                    @NonNls String property) {
        super(label, getPropertyValue(owner, property));
        final ButtonModel model = getModel();
        final SingleCheckboxChangeListener listener =
                new SingleCheckboxChangeListener(owner, property, model);
        model.addChangeListener(listener);
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