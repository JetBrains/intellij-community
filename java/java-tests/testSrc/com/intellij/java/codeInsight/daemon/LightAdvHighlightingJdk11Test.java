// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvHighlightingJdk11Test extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting11";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_11);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_11, getModule(), getTestRootDisposable());//todo
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }

  public void testMixedVarAndExplicitTypesInLambdaDeclaration() {
    doTest();
  }

  public void testGotoDeclarationOnLambdaVarParameter() {
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