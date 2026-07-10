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

/**
 * Standard {@link PostfixModExpander} implementation that uses a {@link PostfixTemplateExpressionSelector}
 * to resolve candidate expressions and delegates the actual expansion to a {@link ModExpandAction}.
 * <p>
 * When multiple candidate expressions are found, the user is presented with a chooser
 * via {@link ModCommand#chooseAction}; for a single candidate the expansion proceeds immediately.
 * <p>
 * Subclasses of {@link PostfixTemplateWithExpressionSelector} can obtain an instance via
 * {@link PostfixTemplateWithExpressionSelector#createModExpander(ModExpandAction)}.
 *
 * @see PostfixModExpander
 */
@ApiStatus.Experimental
public class ExpressionSelectorModExpander implements PostfixModExpander {
  /**
   * Callback that performs the actual per-element template expansion on a non-physical PSI copy.
   * <p>
   * Invoked by {@link ExpressionSelectorModExpander} after the target expression has been resolved
   * and the file copy has been prepared. The {@code element} is already a writable element
   * inside the non-physical copy.
   *
   * @see StringBasedModExpandAction
   * @see SurroundModExpandAction
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
      provider.prepareCopyForModCommand(copyFile, startOffset);
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
            return prepareAndExpandModForChooseExpression(ctx, new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()), expr,
                                                          provider);
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
    return PostfixModExpander.psiUpdateRemovingTemplateKey(ctx, key, updater -> {
      provider.prepareCopyForModCommand(updater.getPsiFile(), PostfixLiveTemplate.positiveOffset(key.getStartOffset()));
      PsiElement elementInCopy = PsiTreeUtil.findSameElementInCopy(virtualExpression, updater.getPsiFile());
      myExpandAction.expand(ctx, updater, elementInCopy);
    });
  }
}