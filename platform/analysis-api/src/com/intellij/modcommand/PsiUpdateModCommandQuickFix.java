// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * A helper abstract class to implement {@link ModCommandQuickFix} for simple quick-fixes that
 * depend on {@link ProblemDescriptor#getStartElement()} only and can be implemented using {@link ModPsiUpdater} API.
 * <p>
 * Many simple classic quick-fixes can be easily converted to ModCommand API using {@code PsiUpdateModCommandQuickFix}.
 * Usually, one needs the following steps:
 * <ol>
 *   <li>Change <code>implements LocalQuickFix</code> to <code>extends PsiUpdateModCommandQuickFix</code>
 *   <li>Override {@link #applyFix(Project, PsiElement, ModPsiUpdater)}
 *   <li>Move {@link #applyFix(Project, ProblemDescriptor)} body into {@link #applyFix(Project, PsiElement, ModPsiUpdater)}
 *   <li>Replace uses of {@link ProblemDescriptor#getStartElement()} or {@link ProblemDescriptor#getPsiElement()}
 *   simply with {@code element}.
 * </ol>
 * <p>
 * One may need additional steps if the quick-fix is more complex. If it tries to manipulate the caret position,
 * launches the template, or requests to rename the created element, it's usually possible to rewrite using the corresponding
 * {@link ModPsiUpdater} methods.
 * <p>
 * {@code PsiUpdateModCommandQuickFix} subclasses should not implement {@link ModCommandAction} or {@link IntentionAction}
 * interfaces. If you already have a {@link ModCommandAction} and want to use it as a quick-fix, adapt via
 * {@link LocalQuickFix#from(ModCommandAction)}.
 * 
 * @see ModCommandQuickFix
 * @see ModPsiUpdater
 */
public abstract class PsiUpdateModCommandQuickFix extends ModCommandQuickFix {
  @Override
  public final @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    return ModCommand.psiUpdate(descriptor.getStartElement(), (e, updater) -> applyFix(project, e, updater));
  }

  protected abstract void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater);
}
