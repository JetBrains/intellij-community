// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMember;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class JavaStaticMemberTypeIndex extends StringStubIndexExtension<PsiMember> {
  private static final JavaStaticMemberTypeIndex ourInstance = new JavaStaticMemberTypeIndex();

  public static JavaStaticMemberTypeIndex getInstance() {
    return ourInstance;
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiMember> getKey() {
    return JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES;
  }

  @NotNull
  public Collection<PsiMember> getStaticMembers(@NotNull String shortTypeText, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES, shortTypeText, project, new JavaSourceFilterScope(scope), PsiMember.class);
  }
}