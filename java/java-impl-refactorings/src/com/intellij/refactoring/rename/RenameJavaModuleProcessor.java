// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class RenameJavaModuleProcessor extends RenamePsiElementProcessor {
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
    PsiJavaModule existing = ContainerUtil.getFirstItem(JavaPsiFacade.getInstance(project).findModules(newName, ProjectScope.getProjectScope(project)));
    if (existing != null) {
      result.add(new UnresolvableCollisionUsageInfo(element, existing) {
        @Override
        public String getDescription() {
          return JavaRefactoringBundle.message("rename.module.already.exists", newName);
        }
      });
    }
  }

  /** Renames an element of this processor on the ModPlan engine, see {@link ModPlanBridgeRenameProvider}. */
  @ApiStatus.Internal
  public static final class ModPlanRenameJavaModuleProvider extends ModPlanBridgeRenameProvider {
    public ModPlanRenameJavaModuleProvider() {
      super(RenameJavaModuleProcessor.class);
    }
  }
}
