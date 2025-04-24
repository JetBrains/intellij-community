/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;

public abstract class BaseInspection extends AbstractBaseJavaLocalInspectionTool {
  private String m_shortName = null;

  @Override
  public @NotNull String getShortName() {
    if (m_shortName == null) {
      final Class<? extends BaseInspection> aClass = getClass();
      final String name = aClass.getSimpleName();
      m_shortName = getShortName(name);
      if (m_shortName.equals(name)) {
        throw new AssertionError("class name must end with 'Inspection' to correctly calculate the short name: " + name);
      }
    }
    return m_shortName;
  }

  @Override
  public final @Nls @NotNull String getGroupDisplayName() {
    return GroupDisplayNameUtil.getGroupDisplayName(getClass());
  }

  protected abstract @NotNull @InspectionMessage String buildErrorString(Object... infos);

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return false;
  }

  /**
   * Builds a fix for this inspection based on infos passed to {@link BaseInspectionVisitor#registerError(PsiElement, Object...)}
   * or similar methods.
   * Override this method in a concrete inspection to provide the fix.
   * <p>
   * This method is ignored if {@link #buildFixes(Object...)} is also overridden and returns a non-empty result.
   * Usually, you should override either this method or {@link #buildFixes(Object...)}, but not both.
   *
   * @param infos additional information which was supplied by {@link BaseInspectionVisitor} during error registration.
   * @return a new fix or null if no fix is available
   */
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return null;
  }

  /**
   * Builds fixes based on infos passed to {@link BaseInspectionVisitor#registerError(PsiElement, Object...)}
   * or similar methods.
   * Override this method in a concrete inspection to provide the fixes.
   * <p>
   * Usually, you should override either this method or {@link #buildFix(Object...)}, but not both.
   *
   * @param infos additional information which was supplied by {@link BaseInspectionVisitor} during error registration.
   * @return an array of fixes (empty array if no fix is available).
   */
  protected @NotNull LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    return InspectionGadgetsFix.EMPTY_ARRAY;
  }

  /**
   * Writes a boolean option field. Does NOT write when the field has the default value.
   *
   * @param node                 the XML element node the field is written to.
   * @param property             the name of the field
   * @param defaultValueToIgnore the default value. When the field has this value, it is NOT written.
   */
  protected void writeBooleanOption(@NotNull Element node, @NotNull @NonNls String property, boolean defaultValueToIgnore) {
    final Boolean value = ReflectionUtil.getField(this.getClass(), this, boolean.class, property);
    assert value != null;
    if (defaultValueToIgnore == value.booleanValue()) {
      return;
    }
    node.addContent(new Element("option").setAttribute("name", property).setAttribute("value", value.toString()));
  }

  /**
   * Writes an option field of any type that can be converted to a String unconditionally. The field can't be null.
   * @param node  the xml element node the field is written to.
   * @param property  the name of the field.
   */
  protected void writeOption(@NotNull Element node, @NotNull @NonNls String property) {
    final Object value = ReflectionUtil.getField(this.getClass(), this, null, property);
    assert value != null : "field " + property + " not found";
    node.addContent(new Element("option").setAttribute("name", property).setAttribute("value", value.toString()));
  }

  /**
   * Writes fields even if they have a default value.
   *
   * @param node               the XML element node the fields are written to.
   * @param excludedProperties fields with names specified here are not written and have to be handled separately
   */
  protected void defaultWriteSettings(@NotNull Element node, final @NonNls String... excludedProperties) throws WriteExternalException {
    DefaultJDOMExternalizer.write(this, node, field -> {
      final String name = field.getName();
      return !ArrayUtil.contains(name, excludedProperties);
    });
  }

  public abstract BaseInspectionVisitor buildVisitor();

  @Override
  public final @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final PsiFile file = holder.getFile();
    assert file.isPhysical();
    if (!shouldInspect(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final BaseInspectionVisitor visitor = buildVisitor();
    visitor.setProblemsHolder(holder);
    visitor.setOnTheFly(isOnTheFly);
    visitor.setInspection(this);
    return visitor;
  }

  /**
   * To check precondition(s) on the entire file, to prevent doing the check on every PsiElement visited.
   * <p>
   * Useful for performing check which would be the same for all elements in the specified,
   * for example {@link com.intellij.psi.util.PsiUtil#isLanguageLevel5OrHigher(PsiElement)}.
   * When this method returns false, {@link #buildVisitor()} will not be called.
   *
   * @deprecated use {@link #isAvailableForFile(PsiFile)} or {@link #requiredFeatures()}
   */
  @Deprecated
  public boolean shouldInspect(@NotNull PsiFile file) {
    return true;
  }

  protected JFormattedTextField prepareNumberEditor(final @NonNls String fieldName) {
    final NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField valueField = new JFormattedTextField(formatter);
    final Object value = ReflectionUtil.getField(getClass(), this, null, fieldName);
    valueField.setValue(value);
    valueField.setColumns(4);

    // hack to work around text field becoming unusably small sometimes when using GridBagLayout
    valueField.setMinimumSize(valueField.getPreferredSize());

    UIUtil.fixFormattedField(valueField);
    final Document document = valueField.getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent evt) {
        try {
          valueField.commitEdit();
          final Number number = (Number)valueField.getValue();
          ReflectionUtil.setField(BaseInspection.this.getClass(), BaseInspection.this, int.class, fieldName, number.intValue());
        }
        catch (ParseException e) {
          // No luck this time. Will update the field when correct value is entered.
        }
      }
    });
    return valueField;
  }

  @SafeVarargs
  public static void parseString(String string, List<String>... outs) {
    final List<String> strings = StringUtil.split(string, ",");
    for (List<String> out : outs) {
      out.clear();
    }
    final int iMax = strings.size();
    for (int i = 0; i < iMax; i += outs.length) {
      for (int j = 0; j < outs.length; j++) {
        final List<String> out = outs[j];
        if (i + j >= iMax) {
          out.add("");
        }
        else {
          out.add(strings.get(i + j));
        }
      }
    }
  }

  @SafeVarargs
  public static String formatString(List<String>... strings) {
    final StringBuilder buffer = new StringBuilder();
    final int size = strings[0].size();
    if (size > 0) {
      formatString(strings, 0, buffer);
      for (int i = 1; i < size; i++) {
        buffer.append(',');
        formatString(strings, i, buffer);
      }
    }
    return buffer.toString();
  }

  private static void formatString(List<String>[] strings, int index, StringBuilder out) {
    out.append(strings[0].get(index));
    for (int i = 1; i < strings.length; i++) {
      out.append(',');
      List<String> list = strings[i];
      out.append(list.size() > index ? list.get(index) : "");
    }
  }
}
