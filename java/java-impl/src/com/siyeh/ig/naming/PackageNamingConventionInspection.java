// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.options.CommonOptionPanes;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiPackageStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import com.siyeh.ig.PackageGlobalInspection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PackageNamingConventionInspection extends PackageGlobalInspection {

  private static final int DEFAULT_MIN_LENGTH = 3;
  private static final int DEFAULT_MAX_LENGTH = 16;
  private static final String DEFAULT_NAME = "<default>";
  private static final String DEFAULT_REGEX = "[a-z]*";
  /**
   * @noinspection PublicField
   */
  public @NonNls String m_regex = DEFAULT_REGEX;

  /**
   * @noinspection PublicField
   */
  public int m_minLength = DEFAULT_MIN_LENGTH;

  /**
   * @noinspection PublicField
   */
  public int m_maxLength = DEFAULT_MAX_LENGTH;

  private Pattern m_regexPattern = Pattern.compile(m_regex);

  @Override
  public CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    final @NonNls String name = StringUtil.getShortName(refPackage.getQualifiedName());
    if (DEFAULT_NAME.equals(name)) {
      return null;
    }

    final int length = name.length();
    if (length == 0) {
      return null;
    }
    if (length < m_minLength) {
      final String errorString = InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.short", name);
      return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
    if (length > m_maxLength) {
      final String errorString = InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.long", name);
      return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    if (matcher.matches()) {
      return null;
    }
    else {
      final String errorString =
        InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.regex.mismatch", name, m_regex);
      return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    m_regexPattern = Pattern.compile(m_regex);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return CommonOptionPanes.conventions("m_minLength", "m_maxLength", "m_regex");
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onValueSet("m_regex", value -> {
      try {
        m_regexPattern = Pattern.compile(m_regex);
      }
      catch (PatternSyntaxException ignore) {
        m_regex = DEFAULT_REGEX;
        m_regexPattern = Pattern.compile(m_regex);
      }
    });
  }

  boolean isValid(String name) {
    final int length = name.length();
    if (length < m_minLength) {
      return false;
    }
    if (m_maxLength > 0 && length > m_maxLength) {
      return false;
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    return matcher.matches();
  }

  @Override
  public @Nullable LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalPackageNamingConventionInspection(this);
  }

  @SuppressWarnings("InspectionDescriptionNotFoundInspection") // TODO IJPL-166089
  private static class LocalPackageNamingConventionInspection extends BaseSharedLocalInspection<PackageNamingConventionInspection> {

    LocalPackageNamingConventionInspection(PackageNamingConventionInspection inspection) {
      super(inspection);
    }

    @Override
    protected @NotNull String buildErrorString(Object... infos) {
      final String name = (String)infos[0];
      if (name.length() < mySettingsDelegate.m_minLength) {
        return InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.short", name);
      }
      else if (name.length() > mySettingsDelegate.m_maxLength) {
        return InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.long", name);
      }
      else {
        return InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.regex.mismatch",
                                               name, mySettingsDelegate.m_regex);
      }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
      return new BaseInspectionVisitor() {

        @Override
        public void visitPackageStatement(@NotNull PsiPackageStatement statement) {
          final PsiJavaCodeReferenceElement reference = statement.getPackageReference();
          if (reference == null) {
            return;
          }
          final String text = reference.getText();
          int start = 0;
          int index = text.indexOf('.', start);
          while (index > 0) {
            final String name = text.substring(start, index);
            if (!mySettingsDelegate.isValid(name)) {
              registerErrorAtOffset(reference, start, index - start, name);
            }
            start = index + 1;
            index = text.indexOf('.', start);
          }
          final String lastName = text.substring(start);
          if (!lastName.isEmpty() && !mySettingsDelegate.isValid(lastName)) {
            registerErrorAtOffset(reference, start, lastName.length(), lastName);
          }
        }
      };
    }
  }
}
