// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.JavaBundle;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldHandlerBase;
import com.intellij.util.containers.ContainerUtil;
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
    return super.isApplicable(context, copyDocument, newOffset) && !JavaPostfixTemplatesUtils.isInExpressionFile(context);
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
    DumbService.getInstance(expression.getProject())
      .withAlternativeResolveEnabled(() -> expandForChooseExpression(expression, editor));
  }

  @Override
  public @NotNull PostfixModExpander createModExpander() {
    return new PostfixModExpander() {
      @Override
      public @NotNull ModCommand expand(@NotNull ActionContext actionContext,
                                        @NotNull PostfixTemplateProvider provider,
                                        @NotNull TextRange keyRange) {
        Project project = actionContext.project();
        var supportProvider = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE);
        JavaIntroduceFieldHandlerBase handler = (JavaIntroduceFieldHandlerBase)supportProvider.getIntroduceFieldHandler();
        if (handler == null) {
          return ModCommand.nop();
        }
        List<JavaIntroduceFieldHandlerBase.ExpressionToFieldContext.Success> contexts =
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
            List<JavaIntroduceFieldHandlerBase.ExpressionToFieldContext.Success> list = selectedExpressions
              .stream()
              .filter(element -> element instanceof PsiExpression)
              .map(expression -> handler.getContext((PsiExpression)expression))
              .filter(c -> c instanceof JavaIntroduceFieldHandlerBase.ExpressionToFieldContext.Success)
              .map(c -> (JavaIntroduceFieldHandlerBase.ExpressionToFieldContext.Success)c)
              .toList();
            return list;
          });
        if (contexts.isEmpty()) {
          return ModCommand.nop();
        }
        JavaIntroduceFieldHandlerBase.ExpressionToFieldContext.Success context = contexts.getFirst();
        JavaIntroduceFieldHandlerBase.AvailableSettings settings = handler.getAvailableSettings(context);

        List<ModCommandAction> actions = ContainerUtil.mapNotNull(
          settings.places(),
          place -> {
            return new ModCommandAction() {
              @Override
              public @NotNull Presentation getPresentation(@NotNull ActionContext ctx) {
                return Presentation.of(JavaIntroduceFieldHandlerBase.InitializationPlace.getPresentableText(place))
                  .withHighlighting(context.selectedExpr().getTextRange());
              }

              @Override
              public @NotNull ModCommand perform(@NotNull ActionContext ctx) {
                TextRange selection = new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset());
                ActionContext updatedContext = ctx.withSelection(selection).withOffset(keyRange.getStartOffset());

                return ModCommand.psiUpdate(actionContext.withSelection(new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()))
                                              .withOffset(keyRange.getStartOffset()),
                                            document -> document.deleteString(selection.getStartOffset(), selection.getEndOffset()),
                                            updater -> {
                                              updater.getDocument()
                                                .deleteString(PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()),
                                                              ctx.selection().getStartOffset());
                                              PsiDocumentManager.getInstance(ctx.project()).commitDocument(updater.getDocument());
                                              provider.prepareCopyForModCommand(updater.getPsiFile(),
                                                                                PostfixLiveTemplate.positiveOffset(
                                                                                  keyRange.getStartOffset()));
                                              PsiElement elementInCopy =
                                                PsiTreeUtil.findSameElementInCopy(context.selectedExpr(), updater.getPsiFile());
                                              elementInCopy = ElementToWorkOn.getWritable(elementInCopy, updater);
                                              handler.run(elementInCopy, place);
                                            });
              }

              @Override
              public @NotNull String getFamilyName() {
                return RefactoringBundle.message("introduce.field.title");
              }
            };
          }
        );
        if (actions.isEmpty()) return ModCommand.nop();
        return ModCommand.chooseAction(JavaBundle.message("introduce.field.initialize.in.scope"), actions);
      }
    };
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }
}