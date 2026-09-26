// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableModCommandService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

// todo: support for int[].var (parses as .class access!)
public class IntroduceVariablePostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  private static final PostfixTemplateExpressionSelector MY_SELECTOR =
    selectorAllExpressionsWithCurrentOffset(IS_NON_VOID);

  public IntroduceVariablePostfixTemplate() {
    super(null, "var", "T name = expr", MY_SELECTOR, null);
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    // for advanced stuff use ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
    JavaIntroduceVariableHandlerBase handler = (JavaIntroduceVariableHandlerBase)LanguageRefactoringSupport.getInstance()
      .forLanguage(JavaLanguage.INSTANCE)
      .getIntroduceVariableHandler();
    assert handler != null;
    handler.invoke(expression.getProject(), editor, (PsiExpression)expression);
  }

  @Override
  public @NotNull PostfixModExpander createModExpander() {
    return (ActionContext actionContext, PostfixTemplateProvider provider, TextRange keyRange) -> {
      List<PsiExpression> candidates = PostfixIntroduceSite.selectExpressions(actionContext, provider, keyRange, MY_SELECTOR);
      return PostfixIntroduceSite.chooseExpression(actionContext, candidates, MY_SELECTOR,
                                                   (ctx, expr) -> introduceVariableCommand(ctx, expr, provider, keyRange));
    };
  }

  /** Introduces a variable for {@code virtualExpr}, an expression of the copy of the file this template analysed. */
  private static @NotNull ModCommand introduceVariableCommand(@NotNull ActionContext ctx,
                                                              @NotNull PsiExpression virtualExpr,
                                                              @NotNull PostfixTemplateProvider provider,
                                                              @NotNull TextRange keyRange) {
    JavaIntroduceVariableModCommandService service = JavaIntroduceVariableModCommandService.getInstance();
    //noinspection HardCodedStringLiteral
    if (service == null) return ModCommand.nop();
    String familyName = MY_SELECTOR.getRenderer().fun(virtualExpr);
    return service.introduceVariableCommand(ctx, new PostfixIntroduceSite(virtualExpr, provider, keyRange),
                                            service.getContext(virtualExpr), familyName);
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context,
                              @NotNull Document copyDocument, int newOffset) {
    // Non-inplace mode would require a modal dialog, which is not allowed under postfix templates
    EditorSettingsExternalizable editorSettingsExternalizable = EditorSettingsExternalizable.getInstance();
    return (editorSettingsExternalizable == null ||
            editorSettingsExternalizable.isVariableInplaceRenameEnabled()) &&
           super.isApplicable(context, copyDocument, newOffset) &&
           !JavaPostfixTemplatesUtils.isInExpressionFile(context) &&
           JavaIntroduceVariableModCommandService.getInstance() != null;
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    expandForChooseExpression(expression, editor);
  }
}
