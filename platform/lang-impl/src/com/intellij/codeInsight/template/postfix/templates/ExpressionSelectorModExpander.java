// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public class ExpressionSelectorModExpander implements PostfixModExpander {
  /**
   * Action that performs the actual per-element expansion within a ModCommand context.
   */
  @ApiStatus.Experimental
  @FunctionalInterface
  public interface ModExpandAction {
    void expand(@NotNull ActionContext ctx, @NotNull ModPsiUpdater updater, @NotNull PsiElement element);
  }

  private final @NotNull ModExpandAction myExpandAction;
  private final @NotNull PostfixTemplateExpressionSelector mySelector;

  public ExpressionSelectorModExpander(@NotNull PostfixTemplateExpressionSelector selector,
                                       @NotNull ModExpandAction expandAction) {
    myExpandAction = expandAction;
    mySelector = selector;
  }

  @Override
  public @NotNull ModCommand expand(@NotNull ActionContext actionContext,
                                    @NotNull PostfixTemplateProvider provider,
                                    @NotNull TextRange keyRange) {
    Project project = actionContext.project();
    List<PsiElement> expressions = PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(() -> {
      PsiFile copyFile = (PsiFile)actionContext.file().copy();
      Document copyDocument = copyFile.getFileDocument();
      int startOffset = keyRange.getStartOffset();
      startOffset = PostfixLiveTemplate.positiveOffset(startOffset);
      copyDocument.deleteString(startOffset, keyRange.getEndOffset());
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      provider.preCheckModCommand(copyFile, startOffset);
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      PsiElement context = CustomTemplateCallback.getContext(copyFile, PostfixLiveTemplate.positiveOffset(startOffset));
      return mySelector.getExpressions(context, copyFile.getFileDocument(), startOffset);
    });
    if (expressions.isEmpty()) {
      return ModCommand.nop();
    }

    if (expressions.size() == 1) {
      return prepareAndExpandModForChooseExpression(actionContext, keyRange, expressions.getFirst(), provider);
    }

    List<ModCommandAction> actions = ContainerUtil.mapNotNull(
      expressions,
      expr -> {
        //noinspection HardCodedStringLiteral -- expression text is used as chooser item title
        String title = mySelector.getRenderer().fun(expr);
        return new ModCommandAction() {
          @Override
          public @NotNull Presentation getPresentation(@NotNull ActionContext ctx) {
            return Presentation.of(title).withHighlighting(expr.getTextRange());
          }

          @Override
          public @NotNull ModCommand perform(@NotNull ActionContext ctx) {
            return prepareAndExpandModForChooseExpression(ctx, new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()), expr, provider);
          }

          @Override
          public @NotNull String getFamilyName() {
            return title;
          }
        };
      }
    );
    if (actions.isEmpty()) return ModCommand.nop();
    return ModCommand.chooseAction(CodeInsightBundle.message("dialog.title.expressions"), actions);
  }

  private @NotNull ModCommand prepareAndExpandModForChooseExpression(@NotNull ActionContext ctx,
                                                                     @NotNull TextRange key,
                                                                     @NotNull PsiElement virtualExpression,
                                                                     @NotNull PostfixTemplateProvider provider) {
    TextRange selection = new TextRange(key.getStartOffset(), key.getStartOffset());
    ActionContext updatedContext = ctx.withSelection(selection).withOffset(key.getStartOffset());
    return ModCommand.psiUpdate(updatedContext,
                                document -> document.deleteString(ctx.selection().getStartOffset(), ctx.selection().getEndOffset()),
                                updater -> {
                                  updater.getDocument().deleteString(PostfixLiveTemplate.positiveOffset(key.getStartOffset()), ctx.selection().getStartOffset());
                                  PsiDocumentManager.getInstance(ctx.project()).commitDocument(updater.getDocument());
                                  provider.preCheckModCommand(updater.getPsiFile(), PostfixLiveTemplate.positiveOffset(key.getStartOffset()));
                                  PsiElement elementInCopy = PsiTreeUtil.findSameElementInCopy(virtualExpression, updater.getPsiFile());
                                  myExpandAction.expand(updatedContext, updater, elementInCopy);
                                });
  }
}