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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;

public final class InstanceMethodNamingConvention extends NamingConvention<PsiMethod> {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;
  @NonNls public static final String INSTANCE_METHOD_NAMING_CONVENTION = "InstanceMethodNamingConvention";

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("[a-z][A-Za-z\\d]*", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
  }

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("instance.method.naming.convention.element.description");
  }

  @Override
  public boolean isApplicable(PsiMethod member) {
    return !member.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public String getShortName() {
    return INSTANCE_METHOD_NAMING_CONVENTION;
  }
}