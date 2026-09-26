// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ParameterNamingConventionInspectionTest extends LightJavaInspectionTestCase {

  public void testParameterNamingConvention() {
    doTest();
  }

  @Override
  protected @NotNull InspectionProfileEntry getInspection() {
    final ParameterNamingConventionInspection inspection = new ParameterNamingConventionInspection();
    inspection.m_minLength = 3;
    inspection.m_maxLength = 5;
    return inspection;
  }
}