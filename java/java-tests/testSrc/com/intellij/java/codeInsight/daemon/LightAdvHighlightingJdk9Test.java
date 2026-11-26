// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.JavaApiUsageInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class LightAdvHighlightingJdk9Test {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk9()));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting9";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection(),
                          new UncheckedWarningLocalInspection(),
                          new RedundantCastInspection(),
                          new JavaDocReferenceInspection(),
                          new JavaApiUsageInspection());
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_9);
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), myFixture.getTestRootDisposable());
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @Test public void testSafeVarargsApplicability() { doTest(true, false); }
  @Test public void testPrivateInInterfaces() { doTest(false, false); }
  @Test public void testPrivateInInterfacesOverriding() { doTest(false, false); }
  @Test public void testPrivateInInterfacesAnonymousThis() { doTest(false, false); }
  @Test public void testPrivateStaticInterfaceMethodNotInherited() { doTest(false, false); }
  @Test public void testUnderscore() { doTest(false, false); }
  @Test public void testTryWithResources() { doTest(false, false); }

  @Test public void testDiamondsWithAnonymous() { doTest(false, false);}
  @Test public void testDiamondsWithAnonymousRejectInferredFreshVariables() { doTest(false, false);}
  @Test public void testDiamondsWithAnonymousRejectNotAccessibleType() { doTest(false, false);}
  @Test public void testDiamondsWithAnonymousRejectIntersectionType() { doTest(false, false);}
  @Test public void testDiamondsWithAnonymousInsideCallToInfer() { doTest(false, false);}
  @Test public void testDiamondsWithAnonymousDiamond() { doTest(false, false);}
  @Test public void testDiamondsWithAnonymousUncheckedCallInLambda() { doTest(false, false);}

  @Test
  public void testHighlightApiUsages() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_1_8, () -> doTest(false, false));
  }
  @Test
  public void testModuleInfoSuppression() {
    daemonFixture.get().doTest(BASE_PATH + "/module-info.java", true, false);
  }
}
