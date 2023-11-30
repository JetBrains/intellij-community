// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceAllDotInspectionTest extends LightJavaInspectionTestCase {

  public void testSuspiciousRegexExpressionArgument() {
    doTest();
    checkQuickFixAll();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ReplaceAllDotInspection();
  }
}