/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class ExtractMethod15Test extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/extractMethod15/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testCodeDuplicatesWithMultOccurences() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = ExtractMethodTest.performExtractMethod(true, true, getEditor(), getFile(), getProject());
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }


  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }
}