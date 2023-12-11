// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public final class JavaMethodParameterTypesIndex extends StringStubIndexExtension<PsiMethod> {
  private static final JavaMethodParameterTypesIndex ourInstance = new JavaMethodParameterTypesIndex();

  public static JavaMethodParameterTypesIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + 1;
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiMethod> getKey() {
    return JavaStubIndexKeys.METHOD_TYPES;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getMethodParameterTypes(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiMethod> get(@NotNull final String s, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return getMethodParameterTypes(s, project, scope);
  }

  public Collection<PsiMethod> getMethodParameterTypes(@NotNull final String s, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), s, project, new JavaSourceFilterScope(scope), PsiMethod.class);
  }
}