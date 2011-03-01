/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;

import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.NumberFormat;
import java.text.ParseException;

public class SingleIntegerFieldOptionsPanel extends JPanel {

    public SingleIntegerFieldOptionsPanel(String labelString,
                                          final InspectionProfileEntry owner,
                                          @NonNls final String property) {
        this(labelString, owner, property, 2);
    }

    public SingleIntegerFieldOptionsPanel(String labelString,
                                          final InspectionProfileEntry owner,
                                          @NonNls final String property,
                                          int integerFieldColumns) {
        super(new GridBagLayout());
        final JLabel label = new JLabel(labelString);
        final NumberFormat formatter = NumberFormat.getIntegerInstance();
        formatter.setParseIntegerOnly(true);
        final JFormattedTextField valueField =
                new JFormattedTextField(formatter);
        valueField.setValue(getPropertyValue(owner, property));
        valueField.setColumns(integerFieldColumns);
        final Document document = valueField.getDocument();
        document.addDocumentListener(new DocumentAdapter() {
            public void textChanged(DocumentEvent e) {
                try {
                    valueField.commitEdit();
                    setPropertyValue(owner, property,
                            ((Number) valueField.getValue()).intValue());
                } catch (ParseException e1) {
                    // No luck this time
                }
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.left = 4;
        constraints.insets.top = 4;
        constraints.weightx = 0.0;
        constraints.anchor = GridBagConstraints.BASELINE_LEADING;
        constraints.fill = GridBagConstraints.NONE;
        add(label, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.BASELINE_LEADING;
        constraints.fill = GridBagConstraints.NONE;
        add(valueField, constraints);
    }

    private static void setPropertyValue(InspectionProfileEntry owner,
                                         String property, int value) {
        try {
            owner.getClass().getField(property).setInt(owner, value);
        } catch (Exception e) {
            // OK
        }
    }

    private static int getPropertyValue(InspectionProfileEntry owner,
                                        String property) {
        try {
            return owner.getClass().getField(property).getInt(owner);
        } catch (Exception e) {
            return 0;
        }
    }
}