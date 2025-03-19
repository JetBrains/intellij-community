// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class DeprecationInspection extends DeprecationInspectionBase {
  public static final String SHORT_NAME = DeprecationUtil.DEPRECATION_SHORT_NAME;
  public static final String ID = DeprecationUtil.DEPRECATION_ID;
  @Language("jvm-field-name") public static final String IGNORE_METHODS_OF_DEPRECATED_NAME = "IGNORE_METHODS_OF_DEPRECATED";

  public boolean IGNORE_INSIDE_DEPRECATED = true;
  public boolean IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = true;
  public boolean IGNORE_IMPORT_STATEMENTS = true;
  public boolean IGNORE_METHODS_OF_DEPRECATED = true;

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if(!Registry.is("kotlin.deprecation.inspection.enabled", false) && holder.getFile().getLanguage().getID().equals("kotlin"))
      return PsiElementVisitor.EMPTY_VISITOR;

    return ApiUsageUastVisitor.createPsiElementVisitor(
      new DeprecatedApiUsageProcessor(holder, IGNORE_INSIDE_DEPRECATED, IGNORE_ABSTRACT_DEPRECATED_OVERRIDES,
                                      IGNORE_IMPORT_STATEMENTS, IGNORE_METHODS_OF_DEPRECATED,
                                      IGNORE_IN_SAME_OUTERMOST_CLASS, false)
    );
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return "";
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @SuppressWarnings("PatternOverriddenByNonAnnotatedMethod")
  public @NotNull String getID() {
    return ID;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_INSIDE_DEPRECATED", JavaAnalysisBundle.message("ignore.inside.deprecated.members")),
      checkbox("IGNORE_IMPORT_STATEMENTS", JavaAnalysisBundle.message("ignore.inside.non.static.imports")),
      checkbox("IGNORE_ABSTRACT_DEPRECATED_OVERRIDES", JavaAnalysisBundle.message("html.ignore.overrides.of.deprecated.abstract.methods")),
      checkbox(IGNORE_METHODS_OF_DEPRECATED_NAME, JavaAnalysisBundle.message("ignore.members.of.deprecated.classes")),
      getSameOutermostClassCheckBox());
  }
}
