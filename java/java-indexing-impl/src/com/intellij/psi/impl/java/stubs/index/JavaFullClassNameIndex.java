// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.CharSequenceHashStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class JavaFullClassNameIndex extends CharSequenceHashStubIndexExtension<PsiClass> {
  private static final JavaFullClassNameIndex ourInstance = new JavaFullClassNameIndex();

  public static JavaFullClassNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<CharSequence, PsiClass> getKey() {
    return JavaStubIndexKeys.CLASS_FQN;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getClasses(CharSequence, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiClass> get(@NotNull CharSequence name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return getClasses(name, project, scope);
  }

  public Collection<PsiClass> getClasses(@NotNull CharSequence name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), name, project, new JavaSourceFilterScope(scope), PsiClass.class);
  }

  @Override
  public boolean doesKeyMatchPsi(@NotNull CharSequence key, @NotNull PsiClass aClass) {
    return key.equals(aClass.getQualifiedName());
  }
}