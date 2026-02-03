// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MultipleVariablesInDeclarationInspectionTest extends LightJavaInspectionTestCase {

  public void testOnlyWarnArrayDimensions() { doTest(); }
  public void testMultipleVariableDeclaration() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    MultipleVariablesInDeclarationInspection inspection = new MultipleVariablesInDeclarationInspection();
    if ("OnlyWarnArrayDimensions".equals(getTestName(false))) {
      inspection.onlyWarnArrayDimensions = true;
    }
    else {
      inspection.ignoreForLoopDeclarations = false;
    }
    return inspection;
  }
}