// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class NewObjectEqualityInspectionTest extends LightJavaInspectionTestCase {

  public void testNewObjectEquality() {
    doTest();
  }
  public void testPrimitiveAndBoxed() {
    doTest();
  }
  public void testControlFlow() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new NewObjectEqualityInspection();
  }
}