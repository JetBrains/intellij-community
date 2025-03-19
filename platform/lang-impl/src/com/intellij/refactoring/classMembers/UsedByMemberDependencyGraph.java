// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.classMembers;

import com.intellij.lang.LanguageDependentMembersRefactoringSupport;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class UsedByMemberDependencyGraph<T extends NavigatablePsiElement, C extends PsiElement, M extends MemberInfoBase<T>> implements MemberDependencyGraph<T, M> {
  private final HashSet<T> mySelectedNormal;
  private final HashSet<T> mySelectedAbstract;
  private final HashSet<T> myMembers;
  private HashSet<T> myDependencies = null;
  private HashMap<T, HashSet<T>> myDependenciesToDependent = null;
  private final MemberDependenciesStorage<T, C> myMemberDependenciesStorage;

  public UsedByMemberDependencyGraph(C aClass) {
    myMemberDependenciesStorage = new MemberDependenciesStorage<>(aClass, null);
    mySelectedNormal = new HashSet<>();
    mySelectedAbstract = new HashSet<>();
    myMembers = new HashSet<>();
  }

  @Override
  public synchronized void memberChanged(M memberInfo) {
    final ClassMembersRefactoringSupport support =
      LanguageDependentMembersRefactoringSupport.INSTANCE.forLanguage(memberInfo.getMember().getLanguage());
    if (support != null && support.isProperMember(memberInfo)) {
      myDependencies = null;
      myDependenciesToDependent = null;
      T member = memberInfo.getMember();
      myMembers.add(member);
      if (!memberInfo.isChecked()) {
        mySelectedNormal.remove(member);
        mySelectedAbstract.remove(member);
      }
      else {
        if (memberInfo.isToAbstract()) {
          mySelectedNormal.remove(member);
          mySelectedAbstract.add(member);
        }
        else {
          mySelectedNormal.add(member);
          mySelectedAbstract.remove(member);
        }
      }
    }
  }

  @Override
  public synchronized Set<? extends T> getDependent() {
    if(myDependencies == null) {
      HashSet<T> dependencies = new HashSet<>();
      HashMap<T, HashSet<T>> dependenciesToDependent = new HashMap<>();
      for (T member : myMembers) {
        Set<T> dependent = myMemberDependenciesStorage.getMemberDependencies(member);
        if (dependent != null) {
          for (final T aDependent : dependent) {
            if (mySelectedNormal.contains(aDependent) && !mySelectedAbstract.contains(aDependent)) {
              dependencies.add(member);
              HashSet<T> deps = dependenciesToDependent.get(member);
              if (deps == null) {
                deps = new HashSet<>();
                dependenciesToDependent.put(member, deps);
              }
              deps.add(aDependent);
            }
          }
        }
      }
      myDependencies = dependencies;
      myDependenciesToDependent = dependenciesToDependent;
    }

    return myDependencies;
  }

  @Override
  public synchronized Set<? extends T> getDependenciesOf(T member) {
    final Set<? extends T> dependent = getDependent();
    if(!dependent.contains(member)) return null;
    return myDependenciesToDependent.get(member);
  }

  public @NlsContexts.Tooltip String getElementTooltip(T element) {
    final Set<? extends T> dependencies = getDependenciesOf(element);
    if (dependencies == null || dependencies.isEmpty()) return null;

    ArrayList<String> strings = new ArrayList<>();
    for (T dep : dependencies) {
      if (dep instanceof PsiNamedElement) {
        strings.add(dep.getName());
      }
    }

    if (strings.isEmpty()) return null;
    return RefactoringBundle.message("uses.0", StringUtil.join(strings, ", "));
  }
}
