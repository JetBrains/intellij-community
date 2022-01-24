// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

// todo: support for int[].var (parses as .class access!)
public class IntroduceVariablePostfixTemplate extends PostfixTemplateWithExpressionSelector {
  public IntroduceVariablePostfixTemplate() {
    super("var", "T name = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    // for advanced stuff use ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
    JavaIntroduceVariableHandlerBase handler = ApplicationManager.getApplication().isUnitTestMode()
                                          ? getMockHandler()
                                          : (JavaIntroduceVariableHandlerBase)LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE)
                                            .getIntroduceVariableHandler();
    assert handler != null;
    handler.invoke(expression.getProject(), editor, (PsiExpression)expression);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context,
                              @NotNull Document copyDocument, int newOffset) {
    // Non-inplace mode would require a modal dialog, which is not allowed under postfix templates 
    return EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled() &&
           super.isApplicable(context, copyDocument, newOffset);
  }

  private static JavaIntroduceVariableHandlerBase getMockHandler() {
    return JavaSpecialRefactoringProvider.getInstance().getMockIntroduceVariableHandler();
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    expandForChooseExpression(expression, editor);
  }
}