// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.options.JavaConfigurationDialogKind;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.group;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.separator;

public class NullableStuffInspection extends NullableStuffInspectionBase {
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
            checkbox("REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED",
                     JavaBundle.message("inspection.nullable.problems.option.notnull.parameter.overrides.not.annotated"))
              .description(JavaBundle.message("inspection.nullable.problems.option.notnull.parameter.overrides.not.annotated.description")),
            checkbox("REPORT_NULLABLE_PARAMETER_OVERRIDES_NOTNULL",
                     JavaBundle.message("inspection.nullable.problems.option.nullable.parameter.overrides.notnull"))
              .description(JavaBundle.message("inspection.nullable.problems.option.nullable.parameter.overrides.notnull.description")),
            checkbox("REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL",
                     JavaBundle.message("inspection.nullable.problems.option.not.annotated.overrides.notnull"))
              .description(JavaBundle.message("inspection.nullable.problems.option.not.annotated.overrides.notnull.description")),
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
}
