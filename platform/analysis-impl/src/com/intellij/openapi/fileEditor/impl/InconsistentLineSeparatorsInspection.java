// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class InconsistentLineSeparatorsInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitFile(@NotNull PsiFile psiFile) {
        if (!psiFile.getLanguage().equals(psiFile.getViewProvider().getBaseLanguage())) {
          // There is a possible case that more than a single virtual file/editor contains more than one language (e.g. php and html).
          // We want to process a virtual file once than, hence, ignore all non-base psi files.
          return;
        }

        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null || !AbstractConvertLineSeparatorsAction.shouldProcess(virtualFile)) {
          return;
        }

        Project project = holder.getProject();
        String projectLineSeparator = FileDocumentManager.getInstance().getLineSeparator(null, project);
        Set<String> allLineSeparators = LoadTextUtil.detectAllLineSeparators(virtualFile);
        if (allLineSeparators.size() > 1 || !allLineSeparators.isEmpty() && !allLineSeparators.contains(projectLineSeparator)) {
          List<String> allSorted = ContainerUtil.sorted(allLineSeparators);
          String presentableSeparators = StringUtil.join(allSorted, sep->StringUtil.escapeStringCharacters(sep), ", ");
          holder.registerProblem(
            psiFile,
            AnalysisBundle.message("inspection.message.line.separators.in.current.file.differ.from.project.defaults", presentableSeparators,
                                   StringUtil.escapeStringCharacters(projectLineSeparator)),
            new ChangeLineSeparatorFix());
        }
      }
    };
  }

  private static class ChangeLineSeparatorFix implements LocalQuickFix {
    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
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
