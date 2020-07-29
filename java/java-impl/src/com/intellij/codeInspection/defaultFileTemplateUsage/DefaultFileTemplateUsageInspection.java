// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultFileTemplateUsageInspection extends AbstractBaseJavaLocalInspectionTool {

  /**
   * @deprecated unused, left for compatibility
   */
  @Deprecated
  public boolean CHECK_FILE_HEADER = true;

  /**
   * @deprecated unused, left for compatibility
   */
  @Deprecated
  public boolean CHECK_TRY_CATCH_SECTION = true;

  /**
   * @deprecated unused, left for compatibility
   */
  @Deprecated
  public boolean CHECK_METHOD_BODY = true;

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return getGeneralGroupName();
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "DefaultFileTemplate";
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    ProblemDescriptor descriptor = FileHeaderChecker.checkFileHeader(file, manager, isOnTheFly);
    return descriptor == null ? null : new ProblemDescriptor[]{descriptor};
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public static LocalQuickFix createEditFileTemplateFix(FileTemplate templateToEdit, ReplaceWithFileTemplateFix replaceTemplateFix) {
    return new EditFileTemplateFix(templateToEdit, replaceTemplateFix);
  }

  private static class EditFileTemplateFix implements LocalQuickFix {
    private final FileTemplate myTemplateToEdit;
    private final ReplaceWithFileTemplateFix myReplaceTemplateFix;

    EditFileTemplateFix(FileTemplate templateToEdit, ReplaceWithFileTemplateFix replaceTemplateFix) {
      myTemplateToEdit = templateToEdit;
      myReplaceTemplateFix = replaceTemplateFix;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaBundle.message("default.file.template.edit.template");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final FileTemplateConfigurable configurable = new FileTemplateConfigurable(project);
      configurable.setTemplate(myTemplateToEdit, null);
      boolean ok = ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
      if (ok) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
          FileTemplateManager.getInstance(project).saveAllTemplates();
          myReplaceTemplateFix.applyFix(project, descriptor);
        });
      }
    }
  }
}
