// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.NavigatableWithText;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public final class ModuleNode extends PackageDependenciesNode implements NavigatableWithText {
  private final @NotNull Module myModule;
  private final ModuleGrouper myModuleGrouper;

  public ModuleNode(@NotNull Module module, @Nullable ModuleGrouper moduleGrouper) {
    super(module.getProject());
    myModule = module;
    myModuleGrouper = moduleGrouper;
  }

  @Override
  public void fillFiles(Set<? super PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      child.fillFiles(set, true);
    }
  }

  @Override
  public boolean canNavigate() {
    return !myModule.isDisposed();
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void navigate(boolean focus) {
    ProjectSettingsService.getInstance(myModule.getProject()).openModuleSettings(myModule);
  }

  @Override
  public Icon getIcon() {
    return myModule.isDisposed() ? super.getIcon() : ModuleType.get(myModule).getIcon();
  }

  @Override
  public String toString() {
    return myModuleGrouper != null ? myModuleGrouper.getShortenedName(myModule) : myModule.getName();
  }

  public String getModuleName() {
    return myModule.getName();
  }

  public @NotNull Module getModule() {
    return myModule;
  }

  @Override
  public int getWeight() {
    return 1;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof ModuleNode moduleNode)) return false;

    return Comparing.equal(myModule, moduleNode.myModule);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }

  @Override
  public boolean isValid() {
    return !myModule.isDisposed();
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    return ActionsBundle.message("action.ModuleSettings.navigate");
  }
}
