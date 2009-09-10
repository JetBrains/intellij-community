/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.refactoring;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.JavaTestUtil;

public class ExtractMethod15Test extends LightCodeInsightTestCase {
  private LanguageLevel myPreviousLanguageLevel;
  private static final String BASE_PATH = "/refactoring/extractMethod15/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected void setUp() throws Exception {
    super.setUp();
    myPreviousLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myPreviousLanguageLevel);
    super.tearDown();
  }

  public void testCodeDuplicatesWithMultOccurences() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = ExtractMethodTest.performExtractMethod(true, true, getEditor(), getFile(), getProject());
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }


  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}