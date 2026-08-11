// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.options.JavaConfigurationDialogKind;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
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
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.group;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.separator;

public class NullableStuffInspection extends NullableStuffInspectionBase {
  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return new NavigateToNullLiteralArguments(parameter);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      group(JavaBundle.message("inspection.nullable.problems.group.overrides"),
            checkbox("REPORT_NULLABLE_RETURN_OVERRIDES_NOTNULL",
                     JavaBundle.message("inspection.nullable.problems.option.nullable.return.overrides.notnull"))
              .description(JavaBundle.message("inspection.nullable.problems.option.nullable.return.overrides.notnull.description")),
            checkbox("REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE",
                     JavaBundle.message("inspection.nullable.problems.option.notnull.parameter.overrides.nullable"))
              .description(JavaBundle.message("inspection.nullable.problems.option.notnull.parameter.overrides.nullable.description")),
            checkbox("REPORT_NULLABLE_PARAMETER_OVERRIDES_NOTNULL",
                     JavaBundle.message("inspection.nullable.problems.option.nullable.parameter.overrides.notnull"))
              .description(JavaBundle.message("inspection.nullable.problems.option.nullable.parameter.overrides.notnull.description")),
            checkbox("REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL",
                     JavaBundle.message("inspection.nullable.problems.option.not.annotated.overrides.notnull"))
              .description(JavaBundle.message("inspection.nullable.problems.option.not.annotated.overrides.notnull.description")),
            checkbox("REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED",
                     JavaBundle.message("inspection.nullable.problems.option.notnull.parameter.overrides.not.annotated"))
              .description(JavaBundle.message("inspection.nullable.problems.option.notnull.parameter.overrides.not.annotated.description")),
            separator(),
            // not nested under a single option on purpose: it also applies to the nullable-parameter check above
            checkbox("IGNORE_EXTERNAL_SUPER_NOTNULL",
                     JavaBundle.message("inspection.nullable.problems.option.ignore.external.notnull"))
              .description(JavaBundle.message("inspection.nullable.problems.option.ignore.external.notnull.description"))),
      group(JavaBundle.message("inspection.nullable.problems.group.generics"),
            checkbox("REPORT_NOT_ANNOTATED_INSTANTIATION_NOT_NULL_TYPE",
                     JavaBundle.message("inspection.nullable.problems.option.unspecified.type.argument"))
              .description(JavaBundle.message("inspection.nullable.problems.option.unspecified.type.argument.description")),
            checkbox("REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS",
                     JavaBundle.message("inspection.nullable.problems.option.notnull.to.nullable.assignment"))
              .description(JavaBundle.message("inspection.nullable.problems.option.notnull.to.nullable.assignment.description"))),
      group(JavaBundle.message("inspection.nullable.problems.group.declarations"),
            checkbox("REPORT_NOT_ANNOTATED_GETTER",
                     JavaBundle.message("inspection.nullable.problems.option.field.accessors.not.annotated"))
              .description(JavaBundle.message("inspection.nullable.problems.option.field.accessors.not.annotated.description")),
            checkbox("REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER",
                     JavaBundle.message("inspection.nullable.problems.option.null.passed.to.notnull.parameter"))
              .description(JavaBundle.message("inspection.nullable.problems.option.null.passed.to.notnull.parameter.description")),
            checkbox("REPORT_NULLABILITY_ANNOTATION_ON_LOCALS",
                     JavaBundle.message("inspection.nullable.problems.option.annotation.on.local.or.catch"))
              .description(JavaBundle.message("inspection.nullable.problems.option.annotation.on.local.or.catch.description")),
            checkbox("REPORT_REDUNDANT_NULLABILITY_ANNOTATION_IN_THE_SCOPE_OF_ANNOTATED_CONTAINER",
                     JavaBundle.message("inspection.nullable.problems.option.redundant.annotation.under.container"))
              .description(JavaBundle.message("inspection.nullable.problems.option.redundant.annotation.under.container.description"))),
      JavaConfigurationDialogKind.NULLABILITY_ANNOTATIONS.button()
    );
  }

  @Override
  public @Nullable @Nls String getStaticDescription() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return "";
    } else {
      return null;
    }
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
