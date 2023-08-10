// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class NonCommentSourceStatementsInspectionTest extends LightJavaInspectionTestCase {

  public void testNonCommentSourceStatements() {
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    final NonCommentSourceStatementsInspection inspection = new NonCommentSourceStatementsInspection();
    inspection.m_limit = 10;
    return inspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }
}