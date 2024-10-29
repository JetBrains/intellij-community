// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class JavaImplicitClassIndex extends StringStubIndexExtension<PsiImplicitClass> {
  private static final JavaImplicitClassIndex ourInstance = new JavaImplicitClassIndex();

  public static JavaImplicitClassIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiImplicitClass> getKey() {
    return JavaStubIndexKeys.IMPLICIT_CLASSES;
  }

  public Collection<String> getAllClasses(@NotNull Project project) {
    return StubIndex.getInstance().getAllKeys(getKey(), project);
  }

  public @NotNull Collection<PsiImplicitClass> getElements(@NotNull String key,
                                                           @NotNull Project project,
                                                           @Nullable GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), key, project, scope, PsiImplicitClass.class);
  }
}