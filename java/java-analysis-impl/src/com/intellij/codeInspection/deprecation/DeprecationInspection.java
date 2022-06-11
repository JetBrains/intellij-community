// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DeprecationInspection extends DeprecationInspectionBase {
  public static final String SHORT_NAME = DeprecationUtil.DEPRECATION_SHORT_NAME;
  public static final String ID = DeprecationUtil.DEPRECATION_ID;
  public static final String IGNORE_METHODS_OF_DEPRECATED_NAME = "IGNORE_METHODS_OF_DEPRECATED";

  public boolean IGNORE_INSIDE_DEPRECATED = true;
  public boolean IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = true;
  public boolean IGNORE_IMPORT_STATEMENTS = true;
  public boolean IGNORE_METHODS_OF_DEPRECATED = true;

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if(!Registry.is("kotlin.deprecation.inspection.enabled", false) && holder.getFile().getLanguage().getID().equals("kotlin"))
      return PsiElementVisitor.EMPTY_VISITOR;

    return ApiUsageUastVisitor.createPsiElementVisitor(
      new DeprecatedApiUsageProcessor(holder, IGNORE_INSIDE_DEPRECATED, IGNORE_ABSTRACT_DEPRECATED_OVERRIDES,
                                      IGNORE_IMPORT_STATEMENTS, IGNORE_METHODS_OF_DEPRECATED,
                                      IGNORE_IN_SAME_OUTERMOST_CLASS, false, false, null)
    );
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @NotNull
  @SuppressWarnings("PatternOverriddenByNonAnnotatedMethod")
  public String getID() {
    return ID;
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(JavaAnalysisBundle.message("ignore.inside.deprecated.members"), "IGNORE_INSIDE_DEPRECATED");
    panel.addCheckbox(JavaAnalysisBundle.message("ignore.inside.non.static.imports"), "IGNORE_IMPORT_STATEMENTS");
    panel.addCheckbox(JavaAnalysisBundle.message("html.ignore.overrides.of.deprecated.abstract.methods"), "IGNORE_ABSTRACT_DEPRECATED_OVERRIDES");
    panel.addCheckbox(JavaAnalysisBundle.message("ignore.members.of.deprecated.classes"), IGNORE_METHODS_OF_DEPRECATED_NAME);
    addSameOutermostClassCheckBox(panel);
    return panel;
  }
}
