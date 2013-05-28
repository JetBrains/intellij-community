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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.Document;
import javax.swing.text.NumberFormatter;
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
        final JFormattedTextField valueField = createIntegerFieldTrackingValue(owner, property, integerFieldColumns);
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.right = UIUtil.DEFAULT_HGAP;
        constraints.weightx = 0.0;
        constraints.anchor = GridBagConstraints.BASELINE_LEADING;
        constraints.fill = GridBagConstraints.NONE;
        add(label, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.insets.right = 0;
        constraints.anchor = GridBagConstraints.BASELINE_LEADING;
        constraints.fill = GridBagConstraints.NONE;
        add(valueField, constraints);
    }

    public static JFormattedTextField createIntegerFieldTrackingValue(@NotNull InspectionProfileEntry owner,
                                                                      @NotNull String property,
                                                                      int integerFieldColumns) {
        JFormattedTextField valueField = new JFormattedTextField();
        valueField.setColumns(integerFieldColumns);
        setupIntegerFieldTrackingValue(valueField, owner, property);
        return valueField;
    }

    /**
     * Sets integer number format to JFormattedTextField instance,
     * sets value of JFormattedTextField instance to object's field value,
     * synchronizes object's field value with the value of JFormattedTextField instance.
     *
     * @param textField  JFormattedTextField instance
     * @param owner      an object whose field is synchronized with {@code textField}
     * @param property   object's field name for synchronization
     */
    public static void setupIntegerFieldTrackingValue(final JFormattedTextField textField,
                                                      final InspectionProfileEntry owner,
                                                      final String property) {
        NumberFormat formatter = NumberFormat.getIntegerInstance();
        formatter.setParseIntegerOnly(true);
        textField.setFormatterFactory(new DefaultFormatterFactory(new NumberFormatter(formatter)));
        textField.setValue(getPropertyValue(owner, property));
        final Document document = textField.getDocument();
        document.addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(DocumentEvent e) {
                try {
                    textField.commitEdit();
                    setPropertyValue(owner, property,
                            ((Number) textField.getValue()).intValue());
                } catch (ParseException e1) {
                    // No luck this time
                }
            }
        });
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
