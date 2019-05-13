// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvLVTIHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advLVTI";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_10);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_10, getModule(), getTestRootDisposable());
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkWarnings) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  public void testSimpleAvailability() { doTest(); }

  public void testDisabledInspections() {
    enableInspectionTool(new AnonymousCanBeLambdaInspection());
    doTest(true);
  }

  public void testKeepSemanticCastForVars() {
    enableInspectionTool(new RedundantCastInspection());
    doTest(true);
  }

  public void testVarClassNameConflicts() { doTest(); }
  public void testStandaloneInVarContext() { doTest(); }

  public void testUpwardProjection() { doTest(); }

  public void testFailedInferenceWithLeftTypeVar() { doTest(); }

  public void testVarInLambdaParameters() {
    setLanguageLevel(LanguageLevel.JDK_11);
    doTest();
  }

  public void testGotoDeclarationOnVar() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement[] elements =
      GotoDeclarationAction.findAllTargetElements(getProject(), getEditor(), offset);
    assertSize(1, elements);
    PsiElement element = elements[0];
    assertInstanceOf(element, PsiClass.class);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, ((PsiClass)element).getQualifiedName());
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }
}