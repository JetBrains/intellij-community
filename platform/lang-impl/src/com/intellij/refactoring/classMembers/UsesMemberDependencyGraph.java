// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.classMembers;

import com.intellij.ide.nls.NlsMessages;
import com.intellij.lang.LanguageDependentMembersRefactoringSupport;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class UsesMemberDependencyGraph<T extends NavigatablePsiElement, C extends PsiElement, M extends MemberInfoBase<T>> implements MemberDependencyGraph<T, M> {
  private static final Logger LOG = Logger.getInstance(UsesMemberDependencyGraph.class);
  private final HashSet<T> mySelectedNormal;
  private final HashSet<T> mySelectedAbstract;
  private HashSet<T> myDependencies = null;
  private HashMap<T, HashSet<T>> myDependenciesToDependentMap = null;
  private final boolean myRecursive;
  private final MemberDependenciesStorage<T, C> myMemberDependenciesStorage;

  public UsesMemberDependencyGraph(C aClass, C superClass, boolean recursive) {
    myRecursive = recursive;
    mySelectedNormal = new HashSet<>();
    mySelectedAbstract = new HashSet<>();
    myMemberDependenciesStorage = new MemberDependenciesStorage<>(aClass, superClass);
  }

  @Override
  public synchronized Set<? extends T> getDependent() {
    if (myDependencies == null) {
      HashSet<T> dependencies = new HashSet<>();
      HashMap<T, HashSet<T>> dependenciesToDependentMap = new HashMap<>();

      buildDeps(mySelectedNormal, dependencies, dependenciesToDependentMap);

      myDependencies = dependencies;
      myDependenciesToDependentMap = dependenciesToDependentMap;
    }
    return myDependencies;
  }

  @Override
  public synchronized Set<? extends T> getDependenciesOf(T member) {
    final Set dependent = getDependent();
    if(!dependent.contains(member)) return null;
    return myDependenciesToDependentMap.get(member);
  }

  public @NlsContexts.Tooltip String getElementTooltip(T element) {
    final Set<? extends T> dependencies = getDependenciesOf(element);
    if(dependencies == null || dependencies.size() == 0) return null;

    String strings = dependencies.stream().map(NavigationItem::getName).collect(NlsMessages.joiningAnd());
    return RefactoringBundle.message("used.by.0", strings);
  }


  private void buildDeps(Set<? extends T> members, HashSet<T> allDependencies, HashMap<T, HashSet<T>> dependenciesToDependentMap) {
    if (myRecursive) {
      buildDepsRecursively(null, members, myDependencies, myDependenciesToDependentMap);
    }
    else {
      for (final T member : members) {
        final Set<T> dependencies = myMemberDependenciesStorage.getMemberDependencies(member);
        if (dependencies != null) {
          for (final T dependency : dependencies) {
            addDependency(dependency, member, allDependencies, dependenciesToDependentMap);
          }
        }
      }
    }
  }

  private void buildDepsRecursively(final T sourceElement,
                                    final @Nullable Set<? extends T> members,
                                    HashSet<T> dependencies,
                                    HashMap<T, HashSet<T>> dependenciesToDependentMap) {
    if (members != null) {
      for (T member : members) {
        if (!dependencies.contains(member)) {
          addDependency(member, sourceElement, dependencies, dependenciesToDependentMap);
          if (!mySelectedAbstract.contains(member)) {
            buildDepsRecursively(member,
                                 myMemberDependenciesStorage.getMemberDependencies(member),
                                 dependencies,
                                 dependenciesToDependentMap);
          }
        }
      }
    }
  }

  private void addDependency(final T member,
                             final T sourceElement,
                             HashSet<T> dependencies,
                             HashMap<T, HashSet<T>> dependenciesToDependentMap) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(member.toString());
    }
    dependencies.add(member);
    if (sourceElement != null) {
      HashSet<T> relations = dependenciesToDependentMap.get(member);
      if (relations == null) {
        relations = new HashSet<>();
        dependenciesToDependentMap.put(member, relations);
      }
      relations.add(sourceElement);
    }
  }

  @Override
  public synchronized void memberChanged(M memberInfo) {
    final ClassMembersRefactoringSupport support =
      LanguageDependentMembersRefactoringSupport.INSTANCE.forLanguage(memberInfo.getMember().getLanguage());
    if (support != null && support.isProperMember(memberInfo)) {
      myDependencies = null;
      myDependenciesToDependentMap = null;
      T member = memberInfo.getMember();
      if (!memberInfo.isChecked()) {
        mySelectedNormal.remove(member);
        mySelectedAbstract.remove(member);
      } else {
        if (memberInfo.isToAbstract()) {
          mySelectedNormal.remove(member);
          mySelectedAbstract.add(member);
        } else {
          mySelectedNormal.add(member);
          mySelectedAbstract.remove(member);
        }
      }
    }
  }
}
