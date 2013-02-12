package com.intellij.codeInsight;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;

/**
 * @author dsl
 */
public class GenerateEqualsTest extends GenerateEqualsTestCase {
  public void testNoBaseEquals() throws Exception {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testPrimitiveFields() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[0], new int[0], false);
  }

  public void testFields() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[0], new int[]{1}, false);
  }

  public void testAbstractSuperEquals() throws Exception {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testSuperEquals() throws Exception {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testHashCode() throws Exception {
    doTest(new int[]{0, 1, 2, 3}, new int[]{0, 1, 2, 3}, new int[]{1}, false);
  }

  public void testArrays() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testOneDoubleField() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneFloatField() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneField() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testNotNull() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testInsertOverride() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }
  
  public void testLangClass() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testLocalLangClass() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testArraysClass() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk14();
  }
}
