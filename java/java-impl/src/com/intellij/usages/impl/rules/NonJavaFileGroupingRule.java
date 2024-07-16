// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.ServerPageFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonJavaFileGroupingRule extends FileGroupingRule {
  public NonJavaFileGroupingRule(Project project) {
    super(project);
  }

  @Override
  public @Nullable UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    final FileUsageGroup usageGroup = (FileUsageGroup)super.getParentGroupFor(usage, targets);
    if (usageGroup != null) {
      final PsiFile psiFile = usageGroup.getPsiFile();
      if (psiFile instanceof PsiJavaFile && !(psiFile instanceof ServerPageFile)) {
        return null;
      }
    }
    return usageGroup;
  }

}
