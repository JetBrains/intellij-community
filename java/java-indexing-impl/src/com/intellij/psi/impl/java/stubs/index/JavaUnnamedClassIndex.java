// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiUnnamedClass;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaUnnamedClassIndex extends StringStubIndexExtension<PsiUnnamedClass> {
  private static final JavaUnnamedClassIndex ourInstance = new JavaUnnamedClassIndex();

  public static JavaUnnamedClassIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion();
  }

  @Override
  public @NotNull StubIndexKey<String, PsiUnnamedClass> getKey() {
    return JavaStubIndexKeys.UNNAMED_CLASSES;
  }

  public Collection<String> getAllClasses(@NotNull Project project) {
    return StubIndex.getInstance().getAllKeys(getKey(), project);
  }
}