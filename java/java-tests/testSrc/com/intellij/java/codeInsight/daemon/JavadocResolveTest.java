// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class JavadocResolveTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/javaDoc/resolve";
  private final JavaDocReferenceInspection myJavaDocReferenceInspection = new JavaDocReferenceInspection();

  public void testSee0() { doTest(); }
  public void testSee1() { doTest(); }
  public void testSee2() { doTest(); }
  public void testSee3() { doTest(); }
  public void testPackageInfo() { doTest("/pkg/package-info.java"); }
  public void testBrokenPackageInfo() { doTest("/pkg1/package-info.java"); }
  public void testModuleInfo() { setLanguageLevel(LanguageLevel.JDK_1_9); doTest("/module-info.java"); }
  public void testOtherPackageLocal() {
    myJavaDocReferenceInspection.REPORT_INACCESSIBLE = false;
    doTest();
  }

  private void doTest() {
    doTest("/pkg/" + getTestName(false) + ".java");
  }

  private void doTest(String testFileName) {
    enableInspectionTools(new JavaDocLocalInspection(), myJavaDocReferenceInspection);
    try {
      doTest(BASE_PATH + testFileName, BASE_PATH, false, false);
    }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }
}