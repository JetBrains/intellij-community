// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.JavaApiUsageInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvHighlightingJdk9Test extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting9";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(),
                          new UncheckedWarningLocalInspection(),
                          new RedundantCastInspection(),
                          new JavaDocReferenceInspection(),
                          new JavaApiUsageInspection());
    setLanguageLevel(LanguageLevel.JDK_1_9);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, getModule(), getTestRootDisposable());
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  public void testSafeVarargsApplicability() { doTest(true, false); }
  public void testPrivateInInterfaces() { doTest(false, false); }
  public void testPrivateInInterfacesOverriding() { doTest(false, false); }
  public void testPrivateInInterfacesAnonymousThis() { doTest(false, false); }
  public void testUnderscore() { doTest(false, false); }
  public void testTryWithResources() { doTest(false, false); }

  public void testDiamondsWithAnonymous() { doTest(false, false);}
  public void testDiamondsWithAnonymousRejectInferredFreshVariables() { doTest(false, false);}
  public void testDiamondsWithAnonymousRejectNotAccessibleType() { doTest(false, false);}
  public void testDiamondsWithAnonymousRejectIntersectionType() { doTest(false, false);}
  public void testDiamondsWithAnonymousInsideCallToInfer() { doTest(false, false);}
  public void testDiamondsWithAnonymousDiamond() { doTest(false, false);}

  public void testHighlightApiUsages() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(false, false);}

  public void testModuleInfoSuppression() {
    doTest(BASE_PATH + "/module-info.java", true, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }
}
