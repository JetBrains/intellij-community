package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.IconUtil;

import javax.swing.*;

public class NonJavaFileGroupingRule extends FileGroupingRule {
  public NonJavaFileGroupingRule(Project project) {
    super(project);
  }

  public UsageGroup groupUsage(Usage usage) {
    final FileUsageGroup usageGroup = (FileUsageGroup)super.groupUsage(usage);
    if (usageGroup != null) {
      final PsiFile psiFile = usageGroup.getPsiFile();
      if (psiFile instanceof PsiJavaFile) {
        return null;
      }
    }
    return usageGroup;
  }

}
