// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageSearcher;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Shows every call site that passes the {@code null} literal to the given parameter.
 */
public class NavigateToNullLiteralArguments extends LocalQuickFixOnPsiElement {
  public NavigateToNullLiteralArguments(@NotNull PsiParameter element) {
    super(element);
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return JavaBundle.message("nullable.stuff.inspection.navigate.null.argument.usages.fix.family.name");
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    PsiParameter p = (PsiParameter)startElement;
    final PsiMethod method = PsiTreeUtil.getParentOfType(p, PsiMethod.class);
    if (method == null) return;
    final int parameterIdx = ArrayUtil.find(method.getParameterList().getParameters(), p);
    if (parameterIdx < 0) return;

    UsageViewPresentation presentation = new UsageViewPresentation();
    String title = JavaBundle.message("nullable.stuff.inspection.navigate.null.argument.usages.view.name", p.getName());
    presentation.setUsagesString(title);
    presentation.setTabName(title);
    presentation.setTabText(title);
    UsageViewManager.getInstance(project).searchAndShowUsages(
      new UsageTarget[]{new PsiElement2UsageTargetAdapter(method.getParameterList().getParameters()[parameterIdx])},
      () -> new UsageSearcher() {
        @Override
        public void generate(final @NotNull Processor<? super Usage> processor) {
          ReadAction.run(() -> JavaNullMethodArgumentUtil.searchNullArgument(method, parameterIdx, (arg) -> processor.process(new UsageInfo2UsageAdapter(new UsageInfo(arg)))));
        }
      }, false, false, presentation, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return new IntentionPreviewInfo.Html(
      JavaBundle.message("nullable.stuff.inspection.navigate.null.argument.usages.fix.family.preview")
    );
  }
}
