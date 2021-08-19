// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

public class UnusedParameterDeclarationTest extends AbstractUnusedDeclarationTest {

  public void testParameterUsedInLambda() {
    doTest();
  }

  public void testParameterUsedInNestedLambda() {
    doTest();
  }

  public void testOnlyParameterUsedInLambda() {
    doTest();
  }

  public void testParameterNotUsedInLambda() {
    doTest();
  }

  public void testParameterNotUsedInNestedLambda() {
    doTest();
  }

  public void testParameterUsedInMethodRef() {
    doTest();
  }

  public void testParameterUsedInAnonymousClass() {
    doTest();
  }

  public void testParameterNotUsedInAnonymousClass() {
    doTest();
  }

  @Override
  protected void doTest() {
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }
}
