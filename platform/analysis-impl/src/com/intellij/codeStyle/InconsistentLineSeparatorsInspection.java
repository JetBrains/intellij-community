/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeStyle;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Nikolay Matveev
 */
public class InconsistentLineSeparatorsInspection extends LocalInspectionTool {
  
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile file) {
        if (!file.getLanguage().equals(file.getViewProvider().getBaseLanguage())) {
          // There is a possible case that more than a single virtual file/editor contains more than one language (e.g. php and html).
          // We want to process a virtual file once than, hence, ignore all non-base psi files.
          return;
        }

        final Project project = holder.getProject();
        final String projectLineSeparator = FileDocumentManager.getInstance().getLineSeparator(null, project);

        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || !AbstractConvertLineSeparatorsAction.shouldProcess(virtualFile, project)) {
          return;
        }

        final String curLineSeparator = LoadTextUtil.detectLineSeparator(virtualFile, true);
        if (curLineSeparator != null && !curLineSeparator.equals(projectLineSeparator)) {
          holder.registerProblem(
            file,
            "Line separators in the current file (" + StringUtil.escapeStringCharacters(curLineSeparator) + ") " +
            "differ from the project defaults (" + StringUtil.escapeStringCharacters(projectLineSeparator) + ")",
            SET_PROJECT_LINE_SEPARATORS);
        }
      }
    };
  }

  @NotNull private static final LocalQuickFix SET_PROJECT_LINE_SEPARATORS = new LocalQuickFix() {
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Convert to project line separators";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (!(psiElement instanceof PsiFile)) {
        return;
      }

      final String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(null, project);

      final VirtualFile virtualFile = ((PsiFile)psiElement).getVirtualFile();
      if (virtualFile != null) {
        AbstractConvertLineSeparatorsAction.changeLineSeparators(project, virtualFile, lineSeparator);
      }
    }
  };
}
