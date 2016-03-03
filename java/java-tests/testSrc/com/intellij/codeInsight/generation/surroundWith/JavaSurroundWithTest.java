/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 5/3/11 2:35 PM
 */
public class JavaSurroundWithTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/generation/surroundWith/java/";

  @SuppressWarnings({"UnusedDeclaration"})
  private enum SurroundType {
    IF(new JavaWithIfSurrounder()),
    IF_ELSE(new JavaWithIfElseSurrounder()),

    WHILE(new JavaWithWhileSurrounder()),
    DO_WHILE(new JavaWithDoWhileSurrounder()),
    FOR(new JavaWithForSurrounder()),

    TRY_CATCH(new JavaWithTryCatchSurrounder()),
    TRY_FINALLY(new JavaWithTryFinallySurrounder()),
    TRY_CATCH_FINALLY(new JavaWithTryCatchFinallySurrounder()),

    SYNCHRONIZED(new JavaWithSynchronizedSurrounder()),
    RUNNABLE(new JavaWithRunnableSurrounder()),
    CODE_BLOCK(new JavaWithBlockSurrounder());

    private final Surrounder mySurrounder;

    SurroundType(Surrounder surrounder) {
      mySurrounder = surrounder;
    }

    public Surrounder getSurrounder() {
      return mySurrounder;
    }

    public String toFileName() {
      StringBuilder result = new StringBuilder();
      boolean capitalize = true;
      for (char c : toString().toCharArray()) {
        if (c == '_') {
          capitalize = true;
          continue;
        }
        if (capitalize) {
          result.append(Character.toUpperCase(c));
          capitalize = false;
        }
        else {
          result.append(Character.toLowerCase(c));
        }
      }
      return result.toString();
    }
  }

  public void testCommentAsFirstSurroundStatement() throws Exception {
    String template = "CommentAsFirst%sSurroundStatement";
    for (SurroundType type : SurroundType.values()) {
      doTest(String.format(template, StringUtil.capitalize(type.toFileName())), type.getSurrounder());
    }
  }

  public void testSurroundNonExpressionWithParenthesis() throws Exception {
    doTest(new JavaWithParenthesesSurrounder());
  }

  public void testSurroundNonExpressionWithCast() throws Exception {
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundExpressionWithCastEmptyLineAfter() throws Exception {
    doTestWithTemplateFinish(getTestName(false), new JavaWithCastSurrounder(), "var");
  }

  public void testSurroundExpressionWithCastEmptyLineAfter_2() throws Exception {
    doTestWithTemplateFinish(getTestName(false), new JavaWithCastSurrounder(), null);
  }

  public void testSurroundNonExpressionWithNot() throws Exception {
    doTest(new JavaWithNotSurrounder());
  }

  public void testSurroundBinaryWithCast() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundConditionalWithCast() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundAssignmentWithCast() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithCastSurrounder());
  }

  public void testSurroundWithNotNullCheck() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithNullCheckSurrounder());
  }

  public void testSurroundExpressionWithIf() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithIfExpressionSurrounder());
  }

  public void testSurroundExpressionWithIfForBoxedBooleans() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithIfExpressionSurrounder());
  }

  public void testSurroundExpressionWithNotForBoxedBooleans() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithNotSurrounder());
  }

  public void testSurroundExpressionWithElseIf() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithIfExpressionSurrounder());
  }

  public void testSurroundExpressionWithElseIfElse() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    doTest(new JavaWithIfElseExpressionSurrounder());
  }

  public void testSurroundWithTryFinallyUsingIndents() {
    CommonCodeStyleSettings.IndentOptions indentOptions = getCurrentCodeStyleSettings().getIndentOptions(JavaFileType.INSTANCE);
    boolean oldUseTabs = indentOptions.USE_TAB_CHARACTER;
    try {
      indentOptions.USE_TAB_CHARACTER = true;
      doTest(new JavaWithTryFinallySurrounder());
    }
    finally {
      indentOptions.USE_TAB_CHARACTER = oldUseTabs;
    }
  }

  public void testSurroundWithTryCatchFunctionalExpression() {
    doTest(new JavaWithTryCatchSurrounder());
  }

  public void testSurroundWithTryCatchProperties() {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    String old = template.getText();
    template.setText("// ${DS} \n" +
                     "${EXCEPTION}.printStackTrace();");
    try {
      doTest(new JavaWithTryCatchSurrounder());
    }
    finally {
      template.setText(old);
    }
  }

  public void testSurroundIfBranchWithNoBracesAndComment() {
    doTest(new JavaWithBlockSurrounder());
  }

  public void testNoParenthesisSurrounderForLambdaParameter() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    SurroundDescriptor item = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE));
    assertNotNull(item);
    SelectionModel selectionModel = getEditor().getSelectionModel();
    PsiElement[] elements = item.getElementsToSurround(getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    assertEmpty(elements);
  }

  private void doTest(Surrounder surrounder) {
    doTest(getTestName(false), surrounder);
  }

  private void doTest(@NotNull String fileName, Surrounder surrounder) {
    configureByFile(BASE_PATH + fileName + ".java");

    SurroundDescriptor item = ContainerUtil.getFirstItem(LanguageSurrounders.INSTANCE.allForLanguage(JavaLanguage.INSTANCE));
    assertNotNull(item);
    SelectionModel selectionModel = getEditor().getSelectionModel();
    PsiElement[] elements = item.getElementsToSurround(getFile(), selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    assertTrue(surrounder.isApplicable(elements));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder);
      }
    });

    checkResultByFile(BASE_PATH + fileName + "_after.java");
  }

  private void doTestWithTemplateFinish(@NotNull String fileName, Surrounder surrounder, @Nullable String textToType) {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    configureByFile(BASE_PATH + fileName + ".java");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder);
      }
    });

    if (textToType != null) {
      type(textToType);
    }
    TemplateState templateState = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(templateState);
    templateState.nextTab();
    checkResultByFile(BASE_PATH + fileName + "_after.java");
  }
}
