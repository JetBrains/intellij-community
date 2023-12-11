// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TestUtils;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class JUnit4MethodNamingConvention extends NamingConvention<PsiMethod> {
  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("junit4.method.naming.convention.element.description");
  }

  @Override
  public boolean isApplicable(PsiMethod member) {
    return TestUtils.isExecutableTestMethod(member, List.of("JUnit4", "JUnit5"));
  }

  @Override
  public String getShortName() {
    return "JUnit4MethodNamingConvention";
  }

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("[a-z][A-Za-z_\\d]*", 4, 64);
  }
}
