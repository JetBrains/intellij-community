// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class JavaMethodNameIndex extends StringStubIndexExtension<PsiMethod> {
  private static final JavaMethodNameIndex ourInstance = new JavaMethodNameIndex();

  public static JavaMethodNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiMethod> getKey() {
    return JavaStubIndexKeys.METHODS;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getMethods(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiMethod> get(final @NotNull String methodName, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return getMethods(methodName, project, scope);
  }

  public @NotNull Collection<PsiMethod> getMethods(final @NotNull String methodName, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), methodName, project, new JavaSourceFilterScope(scope), PsiMethod.class);
  }
}