// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineObjectProcessorUtil.InlineObjectContext;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Performs inlining of object construction together with a subsequent call.
 * E.g. {@code new Point(12, 34).getX()} could be inlined to {@code 12}.
 */
public final class InlineObjectProcessor extends BaseRefactoringProcessor {
  private final InlineObjectContext myContext;

  private InlineObjectProcessor(@NotNull InlineObjectContext context) {
    super(context.project());
    myContext = context;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myContext.method());
  }

  @Override
  protected @NotNull Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return Collections.singletonList(myContext.reference().getElement());
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return InlineObjectProcessorUtil.findUsages(myContext);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    InlineObjectProcessorUtil.performRefactoring(myContext);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usagesIn = refUsages.get();
    final MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
    InlineObjectProcessorUtil.collectConflicts(myContext, usagesIn, conflicts);
    return showConflicts(conflicts, usagesIn);
  }

  @Override
  protected @NotNull String getCommandName() {
    return JavaRefactoringBundle.message("inline.object.command.name");
  }

  public static @Nullable InlineObjectProcessor create(@Nullable InlineObjectContext context) {
    if (context == null) return null;
    return new InlineObjectProcessor(context);
  }

  public static @Nullable InlineObjectProcessor create(PsiReference reference, PsiMethod method) {
    if (!InlineObjectProcessorUtil.canInlineConstructorAndChainCall(reference, method)) {
      return null;
    }
    InlineObjectContext context = InlineObjectContext.create(method, reference);
    return create(context);
  }
}
