// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public class JavaPlatformModuleSystemScope extends DelegatingGlobalSearchScope {
  private final @NotNull PsiJavaModule myModule;

  public JavaPlatformModuleSystemScope(@NotNull Project project, @NotNull PsiJavaModule module, @NotNull GlobalSearchScope baseScope) {
    super(project, baseScope);
    myModule = module;
  }

  public static @NotNull GlobalSearchScope create(@NotNull Project project,
                                                  @NotNull VirtualFile file,
                                                  @NotNull GlobalSearchScope baseScope) {
    PsiJavaModule module = JavaPsiModuleUtil.findDescriptorByFile(file, project);
    if (module == null) return baseScope;
    if (module instanceof LightJavaModule) return baseScope;
    return new JavaPlatformModuleSystemScope(project, module, baseScope);
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    PsiJavaModule module1 = JavaPsiModuleUtil.findDescriptorByFile(file1, myModule.getProject());
    PsiJavaModule module2 = JavaPsiModuleUtil.findDescriptorByFile(file2, myModule.getProject());
    if (module1 == null || module2 == null || // not a module source root
        module1 == module2 || module1.getName().equals(module2.getName()) || // same module
        isModuleFile(module1, file1) || isModuleFile(module2, file2) // the file is a module file
    ) {
      return getDelegate().compare(file1, file2);
    }

    int result = Boolean.compare(JavaPsiModuleUtil.reads(myModule, module1), JavaPsiModuleUtil.reads(myModule, module2));
    return result != 0 ? result : getDelegate().compare(file1, file2);
  }

  private static boolean isModuleFile(@NotNull PsiJavaModule module, @NotNull VirtualFile file) {
    if (module instanceof LightJavaModule) return false;
    return module.getContainingFile().getVirtualFile().equals(file);
  }
}
