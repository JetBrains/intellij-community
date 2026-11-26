// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ColorUtil;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.ui.UIUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LVTI = local variable type inference
 */
@TestApplication
public class LightAdvLVTIHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk9()));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advLVTI";

  @BeforeEach
  void setUp() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_10);
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_10, myFixture.getModule(), myFixture.getTestRootDisposable());
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkWarnings) {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, false);
  }

  @Test
  public void testSimpleAvailability() {
    doTest();
  }

  @Test
  public void testDisabledInspections() {
    daemonFixture.get().getFixture().enableInspections(new AnonymousCanBeLambdaInspection());
    doTest(true);
  }

  @Test
  public void testKeepSemanticCastForVars() {
    daemonFixture.get().getFixture().enableInspections(new RedundantCastInspection());
    doTest(true);
  }

  @Test public void testVarClassNameConflicts() { doTest(); }
  @Test public void testVarUnknownClass() { doTest(); }
  @Test public void testStandaloneInVarContext() { doTest(); }

  @Test public void testUpwardProjection() { doTest(); }

  @Test public void testFailedInferenceWithLeftTypeVar() { doTest(); }
  @Test public void testDisjunctionType() { doTest(); }

  @Test
  public void testRecursiveInference() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      final int offset = myFixture.getEditor().getCaretModel().getOffset();
      PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(offset), PsiMethodCallExpression.class);
      IdempotenceChecker.disableRandomChecksUntil(myFixture.getTestRootDisposable());
      assertTrue(expression.resolveMethodGenerics().isValidResult());
    });
  }

  @Test
  public void testVarInLambdaParameters() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_11);
    doTest();
  }

  @Test public void testWildcardInference() { doTest(); }

  @Test
  public void testGotoDeclarationOnVar() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      final int offset = myFixture.getEditor().getCaretModel().getOffset();
      final PsiElement[] elements =
        GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), offset);
      assertEquals(1, elements.length, Arrays.toString(elements));
      PsiElement element = elements[0];
      assertInstanceOf(PsiClass.class, element);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, ((PsiClass)element).getQualifiedName());
    });
  }
  
  @Test
  public void testIntersectionTypeMethodRef() {
    doTest();
  }
  
  @TestFor(issues = "IDEA-378301")
  @Test
  public void testComplexInheritanceChain() {
    doTest();
  }

  @Test
  public void testTypeObject() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String errorTipForeground = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 8px 4px; color: "+greyed+"\">Required type:</td>" +
                      "<td style=\"padding: 0px 4px 8px 0px;\"><span style=\"color: "+toolTipForeground+"\">anonymous Object</span></td></tr>" +
                      "<tr><td style=\"padding: 0px 16px 0px 4px; color: "+greyed+"\">Provided:</td>" +
                      "<td style=\"padding: 0px 4px 0px 0px;\"><span style=\"color: "+errorTipForeground+"\">Object</span></td></tr>" +
                      "</table>" +
                      "</html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test
  public void testTypeObjectWithAnotherInitializer() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String errorTipForeground = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 8px 4px; color: "+greyed+"\">Required type:</td>" +
                      "<td style=\"padding: 0px 4px 8px 0px;\"><span style=\"color: "+toolTipForeground+"\">anonymous X</span></td></tr>" +
                      "<tr><td style=\"padding: 0px 16px 0px 4px; color: "+greyed+"\">Provided:</td>" +
                      "<td style=\"padding: 0px 4px 0px 0px;\"><span style=\"color: "+errorTipForeground+"\">Y</span></td></tr>" +
                      "</table>" +
                      "</html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }
  
  @Test public void testLeftSideNotAnonymous() { doTest(); }
}