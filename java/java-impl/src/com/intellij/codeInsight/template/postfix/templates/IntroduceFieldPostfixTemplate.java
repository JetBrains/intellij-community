// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldHandlerBase;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldModCommandService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class IntroduceFieldPostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  private static final PostfixTemplateExpressionSelector MY_SELECTOR = selectorAllExpressionsWithCurrentOffset(IS_NON_VOID);

  public IntroduceFieldPostfixTemplate() {
    super("field", "myField = expr", MY_SELECTOR);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return super.isApplicable(context, copyDocument, newOffset) &&
           !JavaPostfixTemplatesUtils.isInExpressionFile(context) &&
           JavaIntroduceFieldModCommandService.getInstance() != null;
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    var supportProvider = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE);
    JavaIntroduceFieldHandlerBase handler = (JavaIntroduceFieldHandlerBase)supportProvider.getIntroduceFieldHandler();
    assert handler != null;
    handler.invoke(expression.getProject(), expression, editor);
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    DumbService.getInstance(expression.getProject()).withAlternativeResolveEnabled(() -> expandForChooseExpression(expression, editor));
  }

  @Override
  public @NotNull PostfixModExpander createModExpander() {
    return (actionContext, provider, keyRange) -> {
      JavaIntroduceFieldModCommandService introduceFieldService = JavaIntroduceFieldModCommandService.getInstance();
      if (introduceFieldService == null) return cannotExtract();
      List<PsiExpression> candidates = PostfixIntroduceSite.selectExpressions(actionContext, provider, keyRange, MY_SELECTOR);
      List<PsiExpression> extractable = ContainerUtil.filter(
        candidates,
        expr -> introduceFieldService.getContext(expr, false) instanceof JavaIntroduceFieldModCommandService.ToFieldContext.Available);
      if (extractable.isEmpty()) return cannotExtract();
      return PostfixIntroduceSite.chooseExpression(actionContext, extractable, MY_SELECTOR,
                                                   (ctx, expr) -> introduceFieldCommand(ctx, expr, provider, keyRange));
    };
  }

  /** Introduces a field for {@code virtualExpr}, an expression of the copy of the file this template analysed. */
  private static @NotNull ModCommand introduceFieldCommand(@NotNull ActionContext ctx,
                                                           @NotNull PsiExpression virtualExpr,
                                                           @NotNull PostfixTemplateProvider provider,
                                                           @NotNull TextRange keyRange) {
    JavaIntroduceFieldModCommandService service = JavaIntroduceFieldModCommandService.getInstance();
    if (service == null) return cannotExtract();
    return service.introduceFieldCommand(ctx, new PostfixToFieldSite(new PostfixIntroduceSite(virtualExpr, provider, keyRange)),
                                         false, service.getContext(virtualExpr, false),
                                         RefactoringBundle.message("introduce.field.title"));
  }

  private static @NotNull ModCommand cannotExtract() {
    return ModCommand.error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
  }

  /** The expression this template found in its analysis copy, which {@code site} finds again in the copy being updated. */
  private record PostfixToFieldSite(@NotNull PostfixIntroduceSite site) implements JavaIntroduceFieldModCommandService.ToFieldSite {
    @Override
    public @NotNull JavaIntroduceFieldModCommandService.ToFieldContext resolve(@NotNull ModPsiUpdater updater) {
      JavaIntroduceFieldModCommandService service = JavaIntroduceFieldModCommandService.getInstance();
      PsiExpression expression = service == null ? null : site.locate(updater);
      if (expression == null) {
        return new JavaIntroduceFieldModCommandService.ToFieldContext.Error(
          JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
      }
      return service.getContext(expression, false);
    }

    @Override
    public @NotNull ModCommand psiUpdate(@NotNull ActionContext context, @NotNull Consumer<@NotNull ModPsiUpdater> action) {
      return site.psiUpdate(context, action);
    }
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }
}
