// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class NullArgumentToVariableArgMethodInspectionTest extends LightJavaInspectionTestCase {

  public void testNullArgumentToVariableArgMethod() {
    doTest();
    checkQuickFixAll();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new NullArgumentToVariableArgMethodInspection();
  }
}
