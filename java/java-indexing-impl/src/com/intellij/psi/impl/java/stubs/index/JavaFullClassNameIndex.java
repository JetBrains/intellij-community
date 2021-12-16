// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.AbstractStubIndex;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.io.CharSequenceHashInlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaFullClassNameIndex extends AbstractStubIndex<CharSequence, PsiClass> {
  private static final JavaFullClassNameIndex ourInstance = new JavaFullClassNameIndex();

  public static JavaFullClassNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public @NotNull KeyDescriptor<CharSequence> getKeyDescriptor() {
    return new CharSequenceHashInlineKeyDescriptor();
  }

  @NotNull
  @Override
  public StubIndexKey<CharSequence, PsiClass> getKey() {
    return JavaStubIndexKeys.CLASS_FQN;
  }

  @Override
  public Collection<PsiClass> get(@NotNull CharSequence name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), name, project, new JavaSourceFilterScope(scope), PsiClass.class);
  }
}