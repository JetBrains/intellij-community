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
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceSite;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Where the {@code field} and {@code var} postfix templates introduce a field or a variable: the template key has to be
 * removed from the copy of the file before the refactoring runs on it, and the expression to extract is the one the
 * template already found in a copy prepared the same way. Holding {@code virtualExpr}, which lives in that analysis
 * copy, is deliberate: it is a tree position for {@link PsiTreeUtil#findSameElementInCopy}, and keeps that copy alive.
 *
 * @param virtualExpr the expression to extract, resolved against the copy the template analysed
 * @param provider    the provider of the expanded template, which may have to pre-process the copy as well
 * @param keyRange    the range of the template key in the original file
 */
record PostfixIntroduceSite(@NotNull PsiExpression virtualExpr,
                            @NotNull PostfixTemplateProvider provider,
                            @NotNull TextRange keyRange) implements IntroduceSite {

  @Override
  public @NotNull ModCommand psiUpdate(@NotNull ActionContext context, @NotNull Consumer<@NotNull ModPsiUpdater> action) {
    return PostfixModExpander.psiUpdateRemovingTemplateKey(context, keyRange, action);
  }

  /**
   * Finds {@link #virtualExpr} in the copy being updated and makes it writable there, which for an expression of a code
   * fragment means rewiring its {@link ElementToWorkOn#PARENT} to that copy.
   */
  @Override
  public @NotNull PsiExpression locate(@NotNull ModPsiUpdater updater) {
    provider.prepareCopyForModCommand(updater.getPsiFile(), PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()));
    PsiExpression expression = PsiTreeUtil.findSameElementInCopy(virtualExpr, updater.getPsiFile());
    return ElementToWorkOn.getWritable(expression, updater);
  }

  /**
   * The expressions {@code selector} offers around the template key, innermost first, empty if there is nothing to
   * extract. They are resolved against a copy prepared the same way {@link #locate} prepares the copy the refactoring
   * runs on, so that an expression found here can be found there again.
   */
  static @NotNull List<@NotNull PsiExpression> selectExpressions(@NotNull ActionContext context,
                                                                 @NotNull PostfixTemplateProvider provider,
                                                                 @NotNull TextRange keyRange,
                                                                 @NotNull PostfixTemplateExpressionSelector selector) {
    Project project = context.project();
    return PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(() -> {
      PsiFile copyFile = (PsiFile)context.file().copy();
      Document copyDocument = copyFile.getFileDocument();
      int startOffset = PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset());
      copyDocument.deleteString(startOffset, keyRange.getEndOffset());
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      provider.prepareCopyForModCommand(copyFile, startOffset);
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      PsiElement elementContext = CustomTemplateCallback.getContext(copyFile, PostfixLiveTemplate.positiveOffset(startOffset));
      List<PsiElement> expressions = selector.getExpressions(elementContext, copyDocument, startOffset);
      return ContainerUtil.filterIsInstance(expressions, PsiExpression.class);
    });
  }

  /**
   * A command extracting one of {@code candidates}, letting the user pick which one when there is more than one.
   *
   * @param candidates the expressions to offer, as {@link #selectExpressions} found them
   * @param selector   the selector which found them, whose renderer names the chooser entries
   * @param command    what extracting a picked expression does, on the context of the round it is picked in
   */
  static @NotNull ModCommand chooseExpression(@NotNull ActionContext context,
                                              @NotNull List<@NotNull PsiExpression> candidates,
                                              @NotNull PostfixTemplateExpressionSelector selector,
                                              @NotNull BiFunction<? super @NotNull ActionContext, ? super @NotNull PsiExpression, @NotNull ModCommand> command) {
    if (candidates.isEmpty()) return ModCommand.nop();
    if (candidates.size() == 1) return command.apply(context, candidates.getFirst());

    List<ModCommandAction> actions = ContainerUtil.map(candidates, expression -> {
      //noinspection HardCodedStringLiteral
      String title = selector.getRenderer().fun(expression);
      TextRange textRange = expression.getTextRange();
      return new ModCommandAction() {
        @Override
        public @NotNull Presentation getPresentation(@NotNull ActionContext actionContext) {
          return Presentation.of(title).withHighlighting(textRange);
        }

        @Override
        public @NotNull ModCommand perform(@NotNull ActionContext actionContext) {
          return command.apply(actionContext, expression);
        }

        @Override
        public @NotNull String getFamilyName() {
          return title;
        }
      };
    });
    return ModCommand.chooseAction(CodeInsightBundle.message("dialog.title.expressions"), actions);
  }
}
