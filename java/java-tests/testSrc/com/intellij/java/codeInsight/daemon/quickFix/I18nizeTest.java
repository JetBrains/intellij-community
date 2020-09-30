/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.i18n.I18nQuickFixHandler;
import com.intellij.codeInspection.i18n.I18nizeAction;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Analogical Kotlin tests: {@link org.jetbrains.idea.devkit.kotlin.quickfix.KtI18nizeTest}
 */
public class I18nizeTest extends LightJavaCodeInsightTestCase {
  @NonNls
  private static String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/i18nize";
  }

  private void doTest() {
    doTest("i18nizedExpr");
  }

  private <T extends UExpression> void doTest(String i18nizedText) {
    configureByFile(getBasePath() + "/before" + getTestName(false) + "." + "java");
    I18nizeAction action = new I18nizeAction();
    DataContext dataContext = DataManager.getInstance().getDataContext(getEditor().getComponent());
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "place", dataContext);
    action.update(event);
    @NonNls String afterFile = getBasePath() + "/after" + getTestName(false) + "." + "java";
    boolean afterFileExists = new File(PathManagerEx.getTestDataPath() + afterFile).exists();
    I18nQuickFixHandler<T> handler = (I18nQuickFixHandler<T>)I18nizeAction.getHandler(event);
    try {
      if (handler != null) {
        handler.checkApplicability(getFile(), getEditor());
      }
    }
    catch (IncorrectOperationException e) {
      event.getPresentation().setEnabled(false);
    }
    assertEquals(afterFileExists, event.getPresentation().isEnabled());

    if (afterFileExists) {
      T literalExpression = handler.getEnclosingLiteral(getFile(), getEditor());
      assertNotNull(handler);
      ApplicationManager.getApplication().runWriteAction(() -> {
        handler.performI18nization(getFile(),
                                   getEditor(),
                                   literalExpression,
                                   Collections.emptyList(),
                                   "key1",
                                   "value1",
                                   i18nizedText,
                                   new UExpression[0], 
                                   JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER);
      });

      checkResultByFile(afterFile);
    }
  }

  public void testLiteral() {doTest();}
  public void testOutsideLiteral() {doTest();}
  public void testLiteralRightSubSelection() {doTest();}
  public void testCaretAtPlus() {doTest();}

  public void testLongConcat() {doTest();}
  public void testCharacterLiteral() {doTest();}
  public void testNestedConcatenation() {doTest();}
  public void testConcatenationInTernary() {doTest();}
  public void testAssignment() {doTest();}

  public void testShortenClassReferences() {
    doTest("p.MyBundle.message(\"key\")");
  }

  public void testGeneratedChoicePattern() {
    configureByFile(getBasePath() + "/before" + getTestName(false) + "." + "java");
    UInjectionHost enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(getFile(), getEditor());
    UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral);
    assertNotNull(concatenation);
    ArrayList<UExpression> args = new ArrayList<>();
    Assert.assertEquals("Not a valid java identifier part in {0, choice, 0#prefix|1#'<'br/'>'suffix}", JavaI18nUtil.buildUnescapedFormatString(concatenation, args, getProject()));
    assertSize(1, args);
    assertEquals("prefix ? 0 : 1", args.get(0).getSourcePsi().getText());
  }

  public void testGeneratedChoicePatternWithConcatenation() {
    configureByFile(getBasePath() + "/before" + getTestName(false) + "." + "java");
    UInjectionHost enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(getFile(), getEditor());
    UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral);
    assertNotNull(concatenation);
    ArrayList<UExpression> args = new ArrayList<>();
    Assert.assertEquals("Not a valid {0} identifier part in {2, choice, 0#{1} prefix|1#suffix}", JavaI18nUtil.buildUnescapedFormatString(concatenation, args, getProject()));
    assertSize(3, args);
    assertEquals("prefix ? 0 : 1", args.get(2).getSourcePsi().getText());
  }
}
