/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.util.regex.Pattern;

/**
 * @author Bas Leijdekkers
 */
public class ConventionOptionsPanel extends JPanel {

  private static final Logger LOG = Logger.getInstance(ConventionOptionsPanel.class);
  @Deprecated
  public ConventionOptionsPanel(@NotNull final InspectionProfileEntry owner,
                                @NonNls final String minLengthProperty, @NonNls final String maxLengthProperty,
                                @NonNls final String regexProperty, @NonNls final String regexPatternProperty,
                                JComponent... extraOptions) {
    this((Object)owner, minLengthProperty, maxLengthProperty, regexProperty, regexPatternProperty, extraOptions);
  }

  public ConventionOptionsPanel(@NotNull final Object owner,
                                @NonNls final String minLengthProperty, @NonNls final String maxLengthProperty,
                                @NonNls final String regexProperty, @NonNls final String regexPatternProperty,
                                JComponent... extraOptions) {
    super(new GridBagLayout());
    final JLabel patternLabel = new JLabel("Pattern:");
    final JLabel minLengthLabel = new JLabel("Min length:");
    final JLabel maxLengthLabel = new JLabel("Max length:");

    final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
    numberFormat.setParseIntegerOnly(true);
    numberFormat.setMinimumIntegerDigits(1);
    numberFormat.setMaximumIntegerDigits(3);
    final InternationalFormatter formatter = new InternationalFormatter(numberFormat);
    formatter.setAllowsInvalid(true);
    formatter.setMinimum(Integer.valueOf(0));
    formatter.setMaximum(Integer.valueOf(999));

    final JFormattedTextField minLengthField = new JFormattedTextField(formatter);
    minLengthField.setValue(getPropertyIntegerValue(owner, minLengthProperty));
    minLengthField.setColumns(2);
    UIUtil.fixFormattedField(minLengthField);

    final JFormattedTextField maxLengthField = new JFormattedTextField(formatter);
    maxLengthField.setValue(getPropertyIntegerValue(owner, maxLengthProperty));
    maxLengthField.setColumns(2);
    UIUtil.fixFormattedField(maxLengthField);

    final JFormattedTextField regexField = new JFormattedTextField(new RegExFormatter());
    regexField.setValue(getPropertyValue(owner, regexPatternProperty));
    regexField.setColumns(25);
    regexField.setInputVerifier(new RegExInputVerifier());
    regexField.setFocusLostBehavior(JFormattedTextField.COMMIT);
    UIUtil.fixFormattedField(regexField);
    final DocumentListener listener = new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent evt) {
        try {
          regexField.commitEdit();
          minLengthField.commitEdit();
          maxLengthField.commitEdit();
          final Pattern pattern = (Pattern)regexField.getValue();
          setPropertyValue(owner, regexPatternProperty, pattern);
          setPropertyValue(owner, regexProperty, pattern.pattern());
          setPropertyIntegerValue(owner, minLengthProperty, ((Integer)minLengthField.getValue()));
          setPropertyIntegerValue(owner, maxLengthProperty, ((Integer)maxLengthField.getValue()));
        }
        catch (Exception e) {
          // No luck this time
        }
      }
    };
    final Document regexDocument = regexField.getDocument();
    regexDocument.addDocumentListener(listener);
    final Document minLengthDocument = minLengthField.getDocument();
    minLengthDocument.addDocumentListener(listener);
    final Document maxLengthDocument = maxLengthField.getDocument();
    maxLengthDocument.addDocumentListener(listener);

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 0.0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    add(patternLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    add(regexField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.weightx = 0.0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    add(minLengthLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 1;
    constraints.weightx = 1;
    constraints.insets.right = 0;
    add(minLengthField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.weightx = 0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    add(maxLengthLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 2;
    constraints.weightx = 1;
    constraints.insets.right = 0;
    add(maxLengthField, constraints);

    constraints.gridx = 0;
    constraints.gridwidth = 2;
    for (JComponent extraOption : extraOptions) {
      constraints.gridy++;
      add(extraOption, constraints);
    }

    constraints.gridy++;
    constraints.weighty = 1.0;
    add(new JPanel(), constraints);
  }

  private static void setPropertyIntegerValue(Object owner, String property, Integer value) {
    setPropertyValue(owner, property, value);
  }

  private static Integer getPropertyIntegerValue(Object owner, String property) {
    return (Integer)getPropertyValue(owner, property);
  }

  private static void setPropertyValue(@NotNull Object owner, String property, Object value) {
    ReflectionUtil.setField(owner.getClass(), owner, null, property, value);
  }

  private static Object getPropertyValue(Object owner, String property) {
    return ReflectionUtil.getField(owner.getClass(), owner, null, property);
  }
}
