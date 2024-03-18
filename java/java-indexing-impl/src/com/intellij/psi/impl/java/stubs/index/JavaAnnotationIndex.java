// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public StubIndexKey<String, PsiAnnotation> getKey() {
    return JavaStubIndexKeys.ANNOTATIONS;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getAnnotations(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiAnnotation> get(@NotNull final String s, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return getAnnotations(s, project, scope);
  }

  public Collection<PsiAnnotation> getAnnotations(@NotNull final String s, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), s, project, new JavaSourceFilterScope(scope), PsiAnnotation.class);
  }
}