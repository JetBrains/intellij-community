// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class JavaShortClassNameIndex extends StringStubIndexExtension<PsiClass> {
  private static final JavaShortClassNameIndex ourInstance = new JavaShortClassNameIndex();

  public static JavaShortClassNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + 2;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiClass> getKey() {
    return JavaStubIndexKeys.CLASS_SHORT_NAMES;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getClasses(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiClass> get(final @NotNull String shortName, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return getClasses(shortName, project, scope);
  }

  public Collection<PsiClass> getClasses(final @NotNull String shortName, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), shortName, project, new JavaSourceFilterScope(scope), PsiClass.class);
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }
}