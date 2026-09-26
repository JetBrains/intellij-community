// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Strategy interface for expanding a postfix template as a {@link ModCommand},
 * suitable for use in ModCompletion and preview.
 *
 * <h3>ModCommand-based postfix template lifecycle</h3>
 * <ol>
 *   <li>{@link PostfixTemplate#isApplicableForModCommand()} — queried to check whether the template
 *       opts in to ModCommand-based expansion.</li>
 *   <li>{@link PostfixTemplate#createModExpander()} — called to obtain this strategy object.
 *       Returns {@code null} if the template does not support ModCommand expansion.</li>
 *   <li>{@link #expand} — invoked with the original {@link ActionContext} and the template key range.
 *       The implementation is responsible for:
 *       <ul>
 *         <li>creating a non-physical copy of the file and deleting the template key from it;</li>
 *         <li>calling {@link PostfixTemplateProvider#prepareCopyForModCommand} if the provider needs
 *             to pre-process the copy (this is <b>not</b> called automatically);</li>
 *         <li>resolving the target expression(s) and performing the actual expansion.</li>
 *       </ul>
 *   </li>
 * </ol>
 * Two standard implementations are provided:
 * <ul>
 *   <li>{@link ExpressionSelectorModExpander} — for templates that use a
 *       {@link PostfixTemplateExpressionSelector} to choose the target expression and a
 *       {@link ExpressionSelectorModExpander.ModExpandAction} to perform the per-element expansion;</li>
 *   <li>{@link com.intellij.codeInsight.template.postfix.templates.editable.EditableTemplateModExpander} —
 *       for {@link com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplate},
 *       which resolves expressions and expands via live template substitution.</li>
 * </ul>
 *
 * @see PostfixTemplate#createModExpander()
 * @see ExpressionSelectorModExpander
 * @see com.intellij.codeInsight.template.postfix.templates.editable.EditableTemplateModExpander
 * @see ExpressionSelectorModExpander.ModExpandAction
 */
@ApiStatus.Experimental
public interface PostfixModExpander {
  /**
   * Expands the template as a {@link ModCommand}, suitable for use in ModCompletion and preview/batch modes.
   * <p>
   * {@code ctx.offset()}, {@code ctx.selection()} and {@code keyRange} are all in the coordinate space of
   * {@code ctx.file()}, which is the injected file in case of injections. Callers that get a host-bound context
   * switch to that space with {@link ActionContext#mapToInjected()} before computing {@code keyRange}.
   */
  @NotNull ModCommand expand(@NotNull ActionContext ctx,
                             @NotNull PostfixTemplateProvider provider,
                             @NotNull TextRange keyRange);

  /**
   * Creates a {@link ModCommand} that deletes the postfix template key from a copy of the file and then
   * runs {@code action} on the resulting {@link ModPsiUpdater} to perform the actual expansion.
   * <p>
   * The key deletion (placing a zero-length selection at the key start, removing the key text and committing
   * the document) is performed before {@code action} runs. Both {@code keyRange} and {@code ctx.selection()}
   * are in {@code ctx.file()} coordinates, which is the injected file in case of injections.
   */
  static @NotNull ModCommand psiUpdateRemovingTemplateKey(@NotNull ActionContext ctx,
                                                          @NotNull TextRange keyRange,
                                                          @NotNull Consumer<? super ModPsiUpdater> action) {
    return ModCommand.psiUpdate(ctx, true, updater -> {
      deleteTemplateKeyAndCommit(updater, ctx, keyRange);
      action.accept(updater);
    });
  }

  /**
   * Maps ranges computed in {@code ctx.file()} coordinates into host coordinates, for use in
   * {@link com.intellij.modcommand.Presentation#withHighlighting(TextRange...)}.
   * <p>
   * Presentation highlighting is resolved against the file of the context the executor runs with, and in completion
   * that is the top-level file, while the expansion itself is computed in the injected fragment. Passing injected
   * ranges as they are highlights the host text at the same numeric offsets, which is a different place entirely.
   * Returns the ranges unchanged when {@code ctx.file()} is not an injected fragment.
   */
  static @NotNull TextRange @NotNull [] rangesToHighlight(@NotNull ActionContext ctx, @NotNull TextRange @NotNull ... ranges) {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(ctx.project());
    PsiFile file = ctx.file();
    if (!manager.isInjectedFragment(file)) return ranges;
    return ContainerUtil.map2Array(ranges, TextRange.class, range -> manager.injectedToHost(file, range));
  }

  private static void deleteTemplateKeyAndCommit(@NotNull ModPsiUpdater updater,
                                                 @NotNull ActionContext ctx,
                                                 @NotNull TextRange keyRange) {
    updater.select(TextRange.from(keyRange.getStartOffset(), 0));
    updater.getDocument().deleteString(PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()),
                                       ctx.selection().getStartOffset());
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(updater.getDocument());
  }
}