// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public StubIndexKey<String, PsiField> getKey() {
    return JavaStubIndexKeys.FIELDS;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getFields(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiField> get(@NotNull final String s, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return getFields(s, project, scope);
  }

  public Collection<PsiField> getFields(@NotNull final String s, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), s, project, new JavaSourceFilterScope(scope), PsiField.class);
  }
}