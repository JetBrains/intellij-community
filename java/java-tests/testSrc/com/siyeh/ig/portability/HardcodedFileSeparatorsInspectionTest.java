// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class HardcodedFileSeparatorsInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testHardcodedFileSeparators() {
    doTest();
  }

  public void testNoCrashOnUnclosedLiteral() { doTest();}

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new HardcodedFileSeparatorsInspection();
  }
}