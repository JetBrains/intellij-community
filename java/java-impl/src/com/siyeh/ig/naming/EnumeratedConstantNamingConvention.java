/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.siyeh.InspectionGadgetsBundle;

public final class EnumeratedConstantNamingConvention extends NamingConvention<PsiField> {

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("enumerated.constant.naming.convention.element.description");
  }

  @Override
  public NamingConventionBean createDefaultBean() {
    return new FieldNamingConventionInspection.FieldNamingConventionBean("[A-Z][A-Z_\\d]*", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
  }

  @Override
  public boolean isApplicable(PsiField member) {
    return member instanceof PsiEnumConstant;
  }

  @Override
  public String getShortName() {
    return "EnumeratedConstantNamingConvention";
  }
}
