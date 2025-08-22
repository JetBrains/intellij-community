// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class JavaAnnotationIndex extends StringStubIndexExtension<PsiAnnotation> {
  private static final JavaAnnotationIndex ourInstance = new JavaAnnotationIndex();

  public static JavaAnnotationIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiAnnotation> getKey() {
    return JavaStubIndexKeys.ANNOTATIONS;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getAnnotations(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiAnnotation> get(final @NotNull String s, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return getAnnotations(s, project, scope);
  }

  public Collection<PsiAnnotation> getAnnotations(
    @NotNull String shortName,
    @NotNull Project project,
    @NotNull GlobalSearchScope scope
  ) {
    return StubIndex.getElements(getKey(), shortName, project, new JavaSourceFilterScope(scope), PsiAnnotation.class);
  }
}