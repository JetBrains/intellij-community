// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.classMembers;

import com.intellij.lang.LanguageDependentMembersRefactoringSupport;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import java.util.HashMap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;


@ApiStatus.Internal
public final class MemberDependenciesStorage<T extends NavigatablePsiElement, C extends PsiElement> {
  private final C myClass;
  private C mySuperClass;
  private final Map<T, Set<T>> myDependencyGraph;

  public MemberDependenciesStorage(C aClass, C superClass) {
    myClass = aClass;
    mySuperClass = superClass;
    myDependencyGraph = new HashMap<>();
  }

  public void setSuperClass(C superClass) {
    mySuperClass = superClass;
  }

  @Nullable Set<T> getMemberDependencies(T member) {
    Set<T> result = myDependencyGraph.get(member);
    if (result == null) {
      DependentMembersCollectorBase<T, C> collector = getCollector(member);
      if (collector != null) {
        collector.collect(member);
        result = collector.getCollection();
      }
      myDependencyGraph.put(member, result);
    }
    return result;
  }

  private DependentMembersCollectorBase<T, C> getCollector(T member) {
    final ClassMembersRefactoringSupport factory = LanguageDependentMembersRefactoringSupport.INSTANCE.forLanguage(member.getLanguage());
    return factory != null ? factory.createDependentMembersCollector(myClass, mySuperClass) : null;
  }
}
