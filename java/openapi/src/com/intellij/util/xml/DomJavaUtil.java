// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DomJavaUtil {
  private DomJavaUtil() {
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name,
                                   @NotNull PsiFile file,
                                   @Nullable final Module module,
                                   @Nullable final GlobalSearchScope searchScope) {
    return findClass(name, file, module, searchScope, false);
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name,
                                   @NotNull PsiFile file,
                                   @Nullable final Module module,
                                   @Nullable GlobalSearchScope searchScope, boolean searchAnonymous) {
    if (name == null) return null;
    searchScope = searchScope != null ? searchScope : calcScope(file, module);
    final PsiClass aClass;
    if (searchAnonymous) {
      aClass = ClassUtil.findPsiClass(file.getManager(), name, null, false, searchScope);
    }
    else {
      String fqn = name.indexOf('$') >= 0 ? name.replace('$', '.') : name;
      aClass = JavaPsiFacade.getInstance(file.getProject()).findClass(fqn, searchScope);
    }
    if (aClass != null) {
      assert aClass.isValid() : name;
    }
    return aClass;
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull DomElement element) {
    return findClass(name, element, false);
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull DomElement element, boolean searchAnonymous) {
    assert element.isValid();
    if (DomUtil.hasXml(element)) {
      return findClass(name, DomUtil.getFile(element), element.getModule(), element.getResolveScope(), searchAnonymous);
    }
    return null;
  }

  @NotNull
  private static GlobalSearchScope calcScope(@NotNull PsiFile file, @Nullable Module module) {
    if (module == null) {
      return file.getResolveScope();
    }

    file = file.getOriginalFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return GlobalSearchScope.moduleRuntimeScope(module, true);
    }

    return module.getModuleRuntimeScope(TestSourcesFilter.isTestSources(virtualFile, file.getProject()));
  }
}
