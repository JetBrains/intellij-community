// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.i18n.I18nQuickFixHandler;
import com.intellij.codeInspection.i18n.I18nizeAction;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Analogical Kotlin tests: {@link org.jetbrains.idea.devkit.kotlin.quickfix.KtI18nizeTest}
 */
@TestApplication
public class I18nizeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @NonNls
  private static String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/i18nize";
  }

  private void doTest() {
    doTest("i18nizedExpr");
  }

  private <T extends UExpression> void doTest(String i18nizedText) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "/before" + myFixture.getTestName(false) + "." + "java");
    @NonNls String afterFile = getBasePath() + "/after" + myFixture.getTestName(false) + "." + "java";
    boolean afterFileExists = new File(JavaTestUtil.getJavaTestDataPath() + afterFile).exists();
    myFixture.runInCommand(() -> {
      I18nizeAction action = new I18nizeAction();
      DataContext dataContext = DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "place", dataContext);
      action.update(event);
      I18nQuickFixHandler<T> handler = (I18nQuickFixHandler<T>)I18nizeAction.getHandler(event);
      try {
        if (handler != null) {
          handler.checkApplicability(myFixture.getFile(), myFixture.getEditor());
        }
      }
      catch (IncorrectOperationException e) {
        event.getPresentation().setEnabled(false);
      }
      assertEquals(afterFileExists, event.getPresentation().isEnabled());

      if (afterFileExists) {
        T literalExpression = handler.getEnclosingLiteral(myFixture.getFile(), myFixture.getEditor());
        assertNotNull(handler);
        ApplicationManager.getApplication().runWriteAction(() -> {
          handler.performI18nization(myFixture.getFile(),
                                     myFixture.getEditor(),
                                     literalExpression,
                                     Collections.emptyList(),
                                     "key1",
                                     "value1",
                                     i18nizedText,
                                     new UExpression[0], 
                                     JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER);
        });
      }
    });

    if (afterFileExists) {
      myFixture.checkResultByFile(afterFile);
    }
  }

  @Test
  public void testLiteral() {doTest();}
  @Test
  public void testOutsideLiteral() {doTest();}
  @Test
  public void testLiteralRightSubSelection() {doTest();}
  @Test
  public void testCaretAtPlus() {doTest();}

  @Test
  public void testLongConcat() {doTest();}
  @Test
  public void testCharacterLiteral() {doTest();}
  @Test
  public void testNestedConcatenation() {doTest();}
  @Test
  public void testConcatenationInTernary() {doTest();}
  @Test
  public void testAssignment() {doTest();}

  @Test
  public void testShortenClassReferences() {
    doTest("p.MyBundle.message(\"key\")");
  }

  @Test
  public void testGeneratedChoicePattern() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "/before" + myFixture.getTestName(false) + "." + "java");
    ReadAction.runBlocking(() -> {
      UInjectionHost enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(myFixture.getFile(), myFixture.getEditor());
      UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral);
      assertNotNull(concatenation);
      ArrayList<UExpression> args = new ArrayList<>();
      assertEquals("Not a valid java identifier part in {0, choice, 0#prefix|1#'<'br/'>'suffix}", JavaI18nUtil.buildUnescapedFormatString(concatenation, args, myFixture.getProject()));
      assertEquals(1, args.size());
      assertEquals("prefix ? 0 : 1", args.get(0).getSourcePsi().getText());
    });
  }

  @Test
  public void testGeneratedChoicePatternWithConcatenation() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "/before" + myFixture.getTestName(false) + "." + "java");
    ReadAction.runBlocking(() -> {
      UInjectionHost enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(myFixture.getFile(), myFixture.getEditor());
      UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral);
      assertNotNull(concatenation);
      ArrayList<UExpression> args = new ArrayList<>();
      assertEquals("Not a valid {0} identifier part in {2, choice, 0#{1} prefix|1#suffix}", JavaI18nUtil.buildUnescapedFormatString(concatenation, args, myFixture.getProject()));
      assertEquals(3, args.size());
      assertEquals("prefix ? 0 : 1", args.get(2).getSourcePsi().getText());
    });
  }
}
