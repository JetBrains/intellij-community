// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LocalVariableNamingConventionInspectionTest extends LightJavaInspectionTestCase {

  public void testLocalVariableNamingConvention() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final LocalVariableNamingConventionInspection inspection = new LocalVariableNamingConventionInspection();
    inspection.m_minLength = 3;
    inspection.m_maxLength = 8;
    return inspection;
  }
}