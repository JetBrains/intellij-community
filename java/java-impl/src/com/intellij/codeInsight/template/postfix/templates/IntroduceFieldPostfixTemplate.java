// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldHandlerBase;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldService;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class IntroduceFieldPostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  public IntroduceFieldPostfixTemplate() {
    super("field", "myField = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return super.isApplicable(context, copyDocument, newOffset) &&
           !JavaPostfixTemplatesUtils.isInExpressionFile(context);
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
      JavaIntroduceFieldService introduceFieldService = JavaIntroduceFieldService.getInstance();
      if (introduceFieldService == null) {
        return ModCommand.error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
      }
      Project project = actionContext.project();
      List<JavaIntroduceFieldService.ToFieldContext.ExpressionContext> contexts =
        PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(() -> {
          PsiFile copyFile = (PsiFile)actionContext.file().copy();
          Document copyDocument = copyFile.getFileDocument();
          int startOffset = keyRange.getStartOffset();
          startOffset = PostfixLiveTemplate.positiveOffset(startOffset);
          copyDocument.deleteString(startOffset, keyRange.getEndOffset());
          PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
          provider.prepareCopyForModCommand(copyFile, startOffset);
          PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
          PsiElement context = CustomTemplateCallback.getContext(copyFile, PostfixLiveTemplate.positiveOffset(startOffset));
          PostfixTemplateExpressionSelector selector = selectorAllExpressionsWithCurrentOffset(IS_NON_VOID);
          List<PsiElement> selectedExpressions = selector.getExpressions(context, copyFile.getFileDocument(), startOffset);
          List<JavaIntroduceFieldService.ToFieldContext.ExpressionContext> list =
            selectedExpressions.stream().filter(element -> element instanceof PsiExpression)
              .map(expression -> introduceFieldService.getContext(expression.getContainingFile(), expression.getTextRange(), false))
              .filter(c -> c instanceof JavaIntroduceFieldService.ToFieldContext.ExpressionContext)
              .map(c -> (JavaIntroduceFieldService.ToFieldContext.ExpressionContext)c).toList();
          return list;
        });
      if (contexts.isEmpty()) {
        return ModCommand.error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
      }

      PsiExpression virtualExpr = contexts.getFirst().selectedExpr();

      List<JavaIntroduceFieldService.InitializationPlace> places =
        JavaIntroduceFieldService.getInstance().getAvailableSettings(virtualExpr).places();

      if (places.isEmpty()) return ModCommand.error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));

      if (places.size() == 1) {
        return buildIntroduceCommand(actionContext, keyRange, virtualExpr, places.getFirst(), provider);
      }

      List<ModCommandAction> actions = ContainerUtil.mapNotNull(places, place -> {
        String presentableText = JavaIntroduceFieldService.InitializationPlace.getPresentableText(place);
        if (presentableText == null) return null;
        return new ModCommandAction() {
          @Override
          public @NotNull Presentation getPresentation(@NotNull ActionContext ctx) {
            return Presentation.of(presentableText).withHighlighting(virtualExpr.getTextRange());
          }

          @Override
          public @NotNull ModCommand perform(@NotNull ActionContext ctx) {
            return buildIntroduceCommand(ctx, new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()),
                                         virtualExpr, place, provider);
          }

          @Override
          public @NotNull String getFamilyName() {
            return RefactoringBundle.message("introduce.field.title");
          }
        };
      });
      if (actions.isEmpty()) return ModCommand.nop();
      return ModCommand.chooseAction(JavaBundle.message("introduce.field.initialize.in.scope"), actions);
    };
  }

  private static @NotNull ModCommand buildIntroduceCommand(@NotNull ActionContext ctx,
                                                           @NotNull TextRange keyRange,
                                                           @NotNull PsiExpression virtualExpr,
                                                           @NotNull JavaIntroduceFieldService.InitializationPlace place,
                                                           @NotNull PostfixTemplateProvider provider) {
    TextRange newSelection = new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset());
    ActionContext updatedContext = ctx.withSelection(newSelection).withOffset(keyRange.getStartOffset());
    JavaIntroduceFieldService introduceFieldService = JavaIntroduceFieldService.getInstance();
    if (introduceFieldService == null) return ModCommand.error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
    return ModCommand.psiUpdate(updatedContext,
                                document -> document.deleteString(ctx.selection().getStartOffset(), ctx.selection().getEndOffset()),
                                updater -> {
                                  updater.getDocument()
                                    .deleteString(PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()),
                                                  ctx.selection().getStartOffset());
                                  PsiDocumentManager.getInstance(ctx.project()).commitDocument(updater.getDocument());
                                  provider.prepareCopyForModCommand(updater.getPsiFile(),
                                                                    PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()));
                                  PsiElement expression = PsiTreeUtil.findSameElementInCopy(virtualExpr, updater.getPsiFile());
                                  expression = ElementToWorkOn.getWritable(expression, updater);
                                  PsiField field = introduceFieldService
                                    .introduceField((PsiExpression)expression, place);
                                  if (field != null) {
                                    List<String> names = new VariableNameGenerator(field, VariableKind.FIELD)
                                      .byExpression(field.getInitializer())
                                      .byType(field.getType())
                                      .generateAll(true);
                                    updater.rename(field, names);
                                  }
                                });
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }
}
