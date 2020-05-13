// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeStyle.AbstractConvertLineSeparatorsAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Nikolay Matveev
 */
public class InconsistentLineSeparatorsInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitFile(@NotNull PsiFile file) {
        if (!file.getLanguage().equals(file.getViewProvider().getBaseLanguage())) {
          // There is a possible case that more than a single virtual file/editor contains more than one language (e.g. php and html).
          // We want to process a virtual file once than, hence, ignore all non-base psi files.
          return;
        }

        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || !AbstractConvertLineSeparatorsAction.shouldProcess(virtualFile)) {
          return;
        }

        Project project = holder.getProject();
        String projectLineSeparator = FileDocumentManager.getInstance().getLineSeparator(null, project);
        Set<String> allLineSeparators = LoadTextUtil.detectAllLineSeparators(virtualFile);
        if (allLineSeparators.size() > 1 || !allLineSeparators.isEmpty() && !allLineSeparators.contains(projectLineSeparator)) {
          List<String> allSorted = new ArrayList<>(allLineSeparators);
          Collections.sort(allSorted);
          String presentableSeparators = StringUtil.join(allSorted, sep->StringUtil.escapeStringCharacters(sep), ", ");
          holder.registerProblem(
            file,
            AnalysisBundle.message("inspection.message.line.separators.in.current.file.differ.from.project.defaults", presentableSeparators,
                                   StringUtil.escapeStringCharacters(projectLineSeparator)),
            new ChangeLineSeparatorFix());
        }
      }
    };
  }

  private static class ChangeLineSeparatorFix implements LocalQuickFix {
    @NotNull
    @IntentionFamilyName
    @Override
    public String getFamilyName() {
      return AnalysisBundle.message("intention.family.name.convert.to.project.line.separators");
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
  }
}
