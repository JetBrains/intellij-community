// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.JavaStaticMethodNameCache;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

@ApiStatus.Experimental
public final class JavaStaticMethodNameCacheImpl extends JavaStaticMethodNameCache {
  @NotNull
  private final Project myProject;

  public JavaStaticMethodNameCacheImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean processMethodsWithName(@NotNull Predicate<String> namePredicate,
                                        @NotNull Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    JavaStaticMemberNameIndex index = JavaStaticMemberNameIndex.getInstance();
    Collection<String> memberNames = index.getAllKeys(myProject);
    for (String memberName : memberNames) {
      if (namePredicate.test(memberName)) {
        Collection<PsiMember> members = index.getStaticMembers(memberName, myProject, scope);
        for (PsiMember member : members) {
          if (member instanceof PsiMethod method) {
            if (!processor.process(method)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public @NotNull Class<? extends PsiShortNamesCache> replaced() {
    return PsiShortNamesCacheImpl.class;
  }
}