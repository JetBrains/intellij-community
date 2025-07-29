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

/**
 * A scope decorator that allows to prefer a "required" module over a module that is a dependency of the required module.
 */
public class JavaPlatformModuleSystemScope extends DelegatingGlobalSearchScope {
  private final @NotNull PsiJavaModule myModule;

  private JavaPlatformModuleSystemScope(@NotNull Project project, @NotNull PsiJavaModule module, @NotNull GlobalSearchScope baseScope) {
    super(project, baseScope);
    myModule = module;
  }

  /**
   * Creates a new instance of {@link JavaPlatformModuleSystemScope} if the file is a part of a JPMS
   * or returns the base scope otherwise.
   */
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
    if (!myModule.isValid()) return getDelegate().compare(file1, file2);
    PsiJavaModule module1 = JavaPsiModuleUtil.findDescriptorByFile(file1, myModule.getProject());
    PsiJavaModule module2 = JavaPsiModuleUtil.findDescriptorByFile(file2, myModule.getProject());

    // Prefer: files from java modules
    if (module1 == null && module2 == null) return getDelegate().compare(file1, file2);
    if (module1 == null) return 1;
    if (module2 == null) return -1;

    // The scope compares imports of classes/packages, so we don't need to compare module files.
    if (isModuleFile(module1, file1) && isModuleFile(module2, file2)) return getDelegate().compare(file1, file2);
    if (isModuleFile(module1, file1)) return 1;
    if (isModuleFile(module2, file2)) return -1;

    // Files from the same module: use base comparator
    if (module1 == module2 || module1.getName().equals(module2.getName())) return getDelegate().compare(file1, file2);

    // Prefer: a file from a module that the current module reads
    int result = Boolean.compare(JavaPsiModuleUtil.reads(myModule, module1), JavaPsiModuleUtil.reads(myModule, module2));
    if (result != 0) return result;

    // Prefer: a file from the current module
    if (module1.equals(myModule)) return -1;
    if (module2.equals(myModule)) return 1;

    return getDelegate().compare(file1, file2);
  }

  private static boolean isModuleFile(@NotNull PsiJavaModule module, @NotNull VirtualFile file) {
    if (module instanceof LightJavaModule) return false;
    return module.getContainingFile().getVirtualFile().equals(file);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    JavaPlatformModuleSystemScope scope = (JavaPlatformModuleSystemScope)o;
    return myModule.equals(scope.myModule);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myModule.hashCode();
  }

  @Override
  public String toString() {
    return "Java module: " + myModule.getName() + " @ " + myBaseScope;
  }
}
