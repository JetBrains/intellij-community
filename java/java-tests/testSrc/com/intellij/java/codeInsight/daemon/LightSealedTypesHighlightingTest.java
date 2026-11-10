// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class LightSealedTypesHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingSealedTypes");
  }

  @Test
  public void testSealedTypesBasics() { doTest(); }
  @Test
  public void testSealedFunctionalInterface() { doTest(); }
  @Test
  public void testSealedRestrictedTypes() { doTest(); }
  @Test
  public void testSealedLocalClass() { doTest(); }
  @Test
  public void testPermitsList() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p1; public class P1 extends p.AnotherPackage {}");
    myFixture.addClass("package p; public class P extends A {}");
    myFixture.addClass("package p1; public sealed class Envelope permits p.Mail {}");
    doTest();
  }
  
  @Test
  public void testPermitsListInLibrarySources() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file =
      (PsiJavaFile)myFixture.addFileToProject("p1/P1.java", "package p1; public sealed interface P1 permits P2 {} final class P2 implements P1 {}");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiJavaFile copy = (PsiJavaFile)file.copy();
      PsiClass[] copyClasses = copy.getClasses();
      copyClasses[0].delete();
      assertNull(JavaErrorCollector.findSingleError(copyClasses[1].getImplementsList().getReferenceElements()[0]));
    });
  }
  
  @Test
  public void testSealedClassCast() { doTest(); }
  
  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}