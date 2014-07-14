/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class DeprecationInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("deprecation/" + getTestName(true), new DeprecationInspection());
  }

  public void testDeprecatedMethod() throws Exception{
    doTest();
  }

  public void testDeprecatedInImport() throws Exception{
    doTest();
  }

  public void testDeprecatedInStaticImport() throws Exception{
    doTest();
  }

  public void testDeprecatedInner() throws Exception {
    doTest();
  }

  public void testDeprecatedField() throws Exception{
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuper() throws Exception {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuperNotCalled() throws Exception {
    doTest();
  }

  public void testDeprecatedDefaultConstructorTypeParameter() throws Exception {
    doTest();
  }

  public void testMethodsOfDeprecatedClass() throws Exception {
    final DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_METHODS_OF_DEPRECATED = false;
    doTest("deprecation/" + getTestName(true), tool);
  }

}
