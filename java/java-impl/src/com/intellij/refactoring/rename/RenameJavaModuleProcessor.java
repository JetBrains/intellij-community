// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class RenameJavaModuleProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PsiJavaModule;
  }

  @Override
  public void findCollisions(@NotNull PsiElement element,
                             @NotNull String newName,
                             @NotNull Map<? extends PsiElement, String> allRenames,
                             @NotNull List<UsageInfo> result) {
    Project project = element.getProject();
    PsiJavaModule existing = ContainerUtil.getFirstItem(JavaModuleNameIndex.getInstance().get(newName, project, ProjectScope.getProjectScope(project)));
    if (existing != null) {
      result.add(new UnresolvableCollisionUsageInfo(element, existing) {
        @Override
        public String getDescription() {
          return RefactoringBundle.message("rename.module.already.exists", newName);
        }
      });
    }
  }
}