// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightSealedTypesHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingSealedTypes";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testSealedTypesBasics() { doTest(); }
  public void testSealedFunctionalInterface() { doTest(); }
  public void testSealedRestrictedTypes() { doTest(); }
  public void testSealedLocalClass() { doTest(); }
  public void testPermitsList() {
    myFixture.addClass("package p1; public class P1 extends p.AnotherPackage {}");
    myFixture.addClass("package p; public class P extends A {}");
    myFixture.addClass("package p1; public sealed class Envelope permits p.Mail {}");
    doTest();
  }
  
  public void testPermitsListInLibrarySources() {
    PsiJavaFile file =
      (PsiJavaFile)myFixture.addFileToProject("p1/P1.java", "package p1; public sealed interface P1 permits P2 {} final class P2 implements P1 {}");
    PsiClass[] classes = file.getClasses();
    LightClass permittedInheritorInCls = new LightClass(classes[1]);
    assertNull(HighlightClassUtil.checkExtendsSealedClass(permittedInheritorInCls, classes[0], classes[0].getPermitsList().getReferenceElements()[0]));
  }
  
  public void testSealedClassCast() { doTest(); }
  
  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}