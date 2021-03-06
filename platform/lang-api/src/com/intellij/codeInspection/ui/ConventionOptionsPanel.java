// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
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
import java.text.NumberFormat;
import java.util.regex.Pattern;

/**
 * @author Bas Leijdekkers
 */
public class ConventionOptionsPanel extends InspectionOptionsPanel {

  private static final Logger LOG = Logger.getInstance(ConventionOptionsPanel.class);
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
    final JLabel patternLabel = new JLabel(InspectionsBundle.message("label.pattern"));
    final JLabel minLengthLabel = new JLabel(InspectionsBundle.message("label.min.length"));
    final JLabel maxLengthLabel = new JLabel(InspectionsBundle.message("label.max.length"));

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
      public void textChanged(@NotNull DocumentEvent evt) {
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

    addRow(patternLabel, regexField);
    addRow(minLengthLabel, minLengthField);
    addRow(maxLengthLabel, maxLengthField);

    for (JComponent extraOption : extraOptions) {
      add(extraOption);
    }
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
