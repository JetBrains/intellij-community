package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.xml.util.CheckTagEmptyBodyInspection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class InjectedLanguageSuppressionTest extends InjectedLanguageTestCase {
  public void testSuppressInjectionInHost() {
    doTest("Suppress for class");
  }

  public void testSuppressInjectionInInjection() {
    doTest("Suppress for tag in injection");
  }

  public void testSuppressInjectionInHostSameLanguage() {
    doTest("Suppress for field");
  }

  public void testSuppressInjectionInInjectionSameLanguage() {
    doTest("Suppress for class in injection");
  }

  public void testDoNotShowHostSuppressionsAvailableInInjectionsOnly() {
    configureByFile("before" + getTestName(false) + ".java");
    bringRealEditorBack();
    List<IntentionAction> actions = getAvailableActions();
    LOG.debug(StringUtil.join(actions, IntentionAction::getText, "\n"));
    assertNull(findActionWithText(actions, "Suppress for statement with comment"));
  }

  public void doTest(final String actionName) {
    configureByFile("before" + getTestName(false) + ".java");
    bringRealEditorBack();
    doAction(actionName);
    PsiTestUtil.checkFileStructure(getFile());
    checkResultByFile("after" + getTestName(false) + ".java");
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new CheckTagEmptyBodyInspection(),
      new SillyAssignmentInspection()
    };
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/injection/suppressions/";
  }
}