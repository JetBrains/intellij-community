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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;

public class AbstractTestClassNamingConvention extends NamingConvention<PsiClass> {

  private static final int DEFAULT_MIN_LENGTH = 12;
  private static final int DEFAULT_MAX_LENGTH = 64;
  public static final @NonNls String ABSTRACT_TEST_CLASS_NAMING_CONVENTION_SHORT_NAME = "JUnitAbstractTestClassNamingConvention";

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("[A-Z][A-Za-z\\d]*TestCase", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
  }

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("junit.abstract.test.class.naming.convention.element.description");
  }

  @Override
  public boolean isApplicable(PsiClass aClass) {
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      return framework != null && framework.isTestClass(aClass);
    }
    return false;
  }

  @Override
  public String getShortName() {
    return ABSTRACT_TEST_CLASS_NAMING_CONVENTION_SHORT_NAME;
  }
}