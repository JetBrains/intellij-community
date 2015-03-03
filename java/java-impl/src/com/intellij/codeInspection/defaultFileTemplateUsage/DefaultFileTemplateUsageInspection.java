/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInspection.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class DefaultFileTemplateUsageInspection extends BaseJavaLocalInspectionTool {
  // Fields are left for the compatibility
  @Deprecated @SuppressWarnings("UnusedDeclaration")
  public boolean CHECK_FILE_HEADER = true;
  @Deprecated @SuppressWarnings("UnusedDeclaration")
  public boolean CHECK_TRY_CATCH_SECTION = true;
  @Deprecated @SuppressWarnings("UnusedDeclaration")
  public boolean CHECK_METHOD_BODY = true;

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("default.file.template.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "DefaultFileTemplate";
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
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

    public EditFileTemplateFix(FileTemplate templateToEdit, ReplaceWithFileTemplateFix replaceTemplateFix) {
      myTemplateToEdit = templateToEdit;
      myReplaceTemplateFix = replaceTemplateFix;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("default.file.template.edit.template");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final FileTemplateConfigurable configurable = new FileTemplateConfigurable(project);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          configurable.setTemplate(myTemplateToEdit, null);
          boolean ok = ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
          if (ok) {
            WriteCommandAction.runWriteCommandAction(project, new Runnable() {
              @Override
              public void run() {
                FileTemplateManager.getInstance(project).saveAllTemplates();
                myReplaceTemplateFix.applyFix(project, descriptor);
              }
            });
          }
        }
      });
    }
  }
}
