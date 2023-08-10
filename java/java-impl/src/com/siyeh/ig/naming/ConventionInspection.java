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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.options.CommonOptionPanes;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.util.InvalidDataException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class ConventionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public String m_regex = getDefaultRegex();
  /**
   * @noinspection PublicField
   */
  public int m_minLength = getDefaultMinLength();
  /**
   * @noinspection PublicField
   */
  public int m_maxLength = getDefaultMaxLength();

  protected Pattern m_regexPattern = Pattern.compile(m_regex);

  @Override
  @NotNull
  protected final String buildErrorString(Object... infos) {
    final String name = (String)infos[0];
    final int length = name.length();
    if (length < getMinLength()) {
      return InspectionGadgetsBundle.message("naming.convention.problem.descriptor.short", getElementDescription(),
                                             Integer.valueOf(length), Integer.valueOf(getMinLength()));
    }
    else if (getMaxLength() > 0 && length > getMaxLength()) {
      return InspectionGadgetsBundle.message("naming.convention.problem.descriptor.long", getElementDescription(),
                                             Integer.valueOf(length), Integer.valueOf(getMaxLength()));
    }
    return InspectionGadgetsBundle.message("naming.convention.problem.descriptor.regex.mismatch", getElementDescription(), getRegex());
  }

  protected abstract String getElementDescription();

  @NonNls
  protected abstract String getDefaultRegex();

  protected abstract int getDefaultMinLength();

  protected abstract int getDefaultMaxLength();

  protected String getRegex() {
    return m_regex;
  }

  protected int getMinLength() {
    return m_minLength;
  }

  protected int getMaxLength() {
    return m_maxLength;
  }

  protected boolean isValid(String name) {
    final int length = name.length();
    if (length < m_minLength) {
      return false;
    }
    if (m_maxLength > 0 && length > m_maxLength) {
      return false;
    }
    if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(name)) {
      return true;
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    return matcher.matches();
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    m_regexPattern = Pattern.compile(m_regex);
  }

  public OptRegularComponent @NotNull [] createExtraOptions() {
    return new OptRegularComponent[0];
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return CommonOptionPanes.conventions(
      "m_minLength", "m_maxLength", "m_regex", createExtraOptions()
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onValueSet("m_regex", value -> {
      try {
        m_regexPattern = Pattern.compile(m_regex);
      }
      catch (PatternSyntaxException ignore) {
        m_regex = getDefaultRegex();
        m_regexPattern = Pattern.compile(m_regex);
      }
    });
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }
}
