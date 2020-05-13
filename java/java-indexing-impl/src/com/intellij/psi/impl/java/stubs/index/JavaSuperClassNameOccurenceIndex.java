// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaSuperClassNameOccurenceIndex extends StringStubIndexExtension<PsiReferenceList> {
  private static final int VERSION = 1;
  private static final JavaSuperClassNameOccurenceIndex ourInstance = new JavaSuperClassNameOccurenceIndex();

  public static JavaSuperClassNameOccurenceIndex getInstance() {
    return ourInstance;
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiReferenceList> getKey() {
    return JavaStubIndexKeys.SUPER_CLASSES;
  }

  @Override
  public Collection<PsiReferenceList> get(@NotNull final String s, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), s, project, new JavaSourceFilterScope(scope), PsiReferenceList.class);
  }

  @Override
  public int getVersion() {
    return super.getVersion() + VERSION;
  }
}