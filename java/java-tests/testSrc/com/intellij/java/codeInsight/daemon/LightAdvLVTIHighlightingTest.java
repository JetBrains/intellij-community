// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.ui.UIUtil;
import org.junit.Assert;

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

  public void testSimpleAvailability() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    doTest();
  }

  public void testDisabledInspections() {
    enableInspectionTool(new AnonymousCanBeLambdaInspection());
    doTest(true);
  }

  public void testKeepSemanticCastForVars() {
    enableInspectionTool(new RedundantCastInspection());
    doTest(true);
  }

  public void testVarClassNameConflicts() { doTest(); }
  public void testVarUnknownClass() { doTest(); }
  public void testStandaloneInVarContext() { doTest(); }

  public void testUpwardProjection() { doTest(); }

  public void testFailedInferenceWithLeftTypeVar() { doTest(); }
  public void testDisjunctionType() { doTest(); }

  public void testRecursiveInference() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final int offset = getEditor().getCaretModel().getOffset();
    PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(getFile().findElementAt(offset), PsiMethodCallExpression.class);
    IdempotenceChecker.disableRandomChecksUntil(getTestRootDisposable());
    assertTrue(expression.resolveMethodGenerics().isValidResult());
  }

  public void testVarInLambdaParameters() {
    setLanguageLevel(LanguageLevel.JDK_11);
    doTest();
  }

  public void testWildcardInference() { doTest(); }

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
  
  public void testIntersectionTypeMethodRef() {
    doTest();
  }

  public void testTypeObject() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 8px 4px;color: "+greyed+"'>Required type:</td>" +
                      "<td style='padding: 0px 4px 8px 0px;'>anonymous <font color=\""+toolTipForeground+"\">Object</font></td></tr>" +
                      "<tr><td style='padding: 0px 16px 0px 4px;color: "+greyed+"'>Provided:</td><td style='padding: 0px 4px 0px 0px;'><font color=\""+toolTipForeground+"\">Object</font></td></tr>" +
                      "</table>" +
                      "</html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  public void testTypeObjectWithAnotherInitializer() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String errorTipForeground = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 8px 4px;color: "+greyed+"'>Required type:</td>" +
                      "<td style='padding: 0px 4px 8px 0px;'>anonymous <font color=\""+toolTipForeground+"\">X</font></td></tr>" +
                      "<tr><td style='padding: 0px 16px 0px 4px;color: "+greyed+"'>Provided:</td><td style='padding: 0px 4px 0px 0px;'><font color=\""+errorTipForeground+"\">Y</font></td></tr>" +
                      "</table>" +
                      "</html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }
}