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

public class JavaMethodNameIndex extends StringStubIndexExtension<PsiMethod> {
  private static final JavaMethodNameIndex ourInstance = new JavaMethodNameIndex();

  public static JavaMethodNameIndex getInstance() {
    return ourInstance;
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiMethod> getKey() {
    return JavaStubIndexKeys.METHODS;
  }

  @Override
  public Collection<PsiMethod> get(@NotNull final String methodName, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), methodName, project, new JavaSourceFilterScope(scope), PsiMethod.class);
  }
}