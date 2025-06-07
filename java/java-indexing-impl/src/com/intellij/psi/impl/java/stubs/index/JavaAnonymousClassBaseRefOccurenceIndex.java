// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class JavaAnonymousClassBaseRefOccurenceIndex extends StringStubIndexExtension<PsiAnonymousClass> {
  private static final JavaAnonymousClassBaseRefOccurenceIndex ourInstance = new JavaAnonymousClassBaseRefOccurenceIndex();

  public static JavaAnonymousClassBaseRefOccurenceIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiAnonymousClass> getKey() {
    return JavaStubIndexKeys.ANONYMOUS_BASEREF;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getOccurences(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiAnonymousClass> get(final @NotNull String s, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return getOccurences(s, project, scope);
  }

  public Collection<PsiAnonymousClass> getOccurences(final @NotNull String s, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), s, project, new JavaSourceFilterScope(scope), PsiAnonymousClass.class);
  }
}