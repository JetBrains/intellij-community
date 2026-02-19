// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class JavaFieldNameIndex extends StringStubIndexExtension<PsiField> {
  private static final JavaFieldNameIndex ourInstance = new JavaFieldNameIndex();

  public static JavaFieldNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiField> getKey() {
    return JavaStubIndexKeys.FIELDS;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getFields(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiField> get(final @NotNull String s, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return getFields(s, project, scope);
  }

  public Collection<PsiField> getFields(final @NotNull String s, final @NotNull Project project, final @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), s, project, new JavaSourceFilterScope(scope), PsiField.class);
  }
}