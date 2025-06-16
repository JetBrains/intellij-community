// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.options.JavaInspectionButtons;
import com.intellij.codeInsight.options.JavaInspectionControls;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
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
import com.intellij.usages.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class NullableStuffInspection extends NullableStuffInspectionBase {
  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return new NavigateToNullLiteralArguments(parameter);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE", JavaBundle.message("inspection.nullable.problems.method.overrides.notnull.option")),
      checkbox("REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL", JavaBundle.message("inspection.nullable.problems.method.overrides.option"),
               checkbox("IGNORE_EXTERNAL_SUPER_NOTNULL", JavaBundle.message("inspection.nullable.problems.ignore.external.notnull"))),
      checkbox("REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED", JavaBundle.message("inspection.nullable.problems.notnull.overrides.option")),
      checkbox("REPORT_NOT_ANNOTATED_GETTER", JavaBundle.message("inspection.nullable.problems.not.annotated.getters.for.annotated.fields")),
      checkbox("REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER", JavaBundle.message("inspection.nullable.problems.notnull.parameters.with.null.literal.option")),
      JavaInspectionControls.button(JavaInspectionButtons.ButtonKind.NULLABILITY_ANNOTATIONS)
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController()
      .onValueSet((bindId, value) -> REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL);
  }

  public static class NavigateToNullLiteralArguments extends LocalQuickFixOnPsiElement {
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
}
