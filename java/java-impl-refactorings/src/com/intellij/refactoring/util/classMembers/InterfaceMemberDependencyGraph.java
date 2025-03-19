// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.classMembers;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberDependencyGraph;
import com.intellij.refactoring.classMembers.MemberInfoBase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class InterfaceMemberDependencyGraph<T extends PsiMember, M extends MemberInfoBase<T>> implements MemberDependencyGraph<T, M> {
  private HashSet<T> myInterfaceDependencies;
  private HashMap<T,HashSet<T>> myMembersToInterfacesMap = new HashMap<>();
  private final HashSet<PsiClass> myImplementedInterfaces;
  private final HashMap<PsiClass,HashSet<T>> myMethodsFromInterfaces;
  private final PsiClass myClass;

  public InterfaceMemberDependencyGraph(PsiClass aClass) {
    myClass = aClass;
    myImplementedInterfaces = new HashSet<>();
    myMethodsFromInterfaces = new HashMap<>();
  }

  @Override
  public synchronized void memberChanged(M memberInfo) {
    if (ClassMembersUtil.isImplementedInterface(memberInfo)) {
      final PsiClass aClass = (PsiClass) memberInfo.getMember();
      myInterfaceDependencies = null;
      myMembersToInterfacesMap = null;
      if(memberInfo.isChecked()) {
        myImplementedInterfaces.add(aClass);
      }
      else {
        myImplementedInterfaces.remove(aClass);
      }
    }
  }

  @Override
  public synchronized Set<? extends T> getDependent() {
    if(myInterfaceDependencies == null) {
      HashSet<T> dependencies = new HashSet<>();
      HashMap<T, HashSet<T>> membersToInterfacesMap = new HashMap<>();
      for (final PsiClass implementedInterface : myImplementedInterfaces) {
        addInterfaceDeps(implementedInterface, dependencies, membersToInterfacesMap);
      }
      myInterfaceDependencies = dependencies;
      myMembersToInterfacesMap = membersToInterfacesMap;
    }
    return myInterfaceDependencies;
  }

  @Override
  public synchronized Set<? extends T> getDependenciesOf(PsiMember member) {
    final Set dependent = getDependent();
    if(dependent.contains(member)) return myMembersToInterfacesMap.get(member);
    return null;
  }

  public @NlsContexts.Tooltip String getElementTooltip(PsiMember member) {
    final Set<? extends PsiMember> dependencies = getDependenciesOf(member);
    if(dependencies == null || dependencies.isEmpty()) return null;
    String interfaces = StringUtil.join(dependencies, PsiMember::getName, ", ");
    return RefactoringBundle.message("interface.member.dependency.required.by.interfaces.list", dependencies.size(), interfaces);
  }

  private void addInterfaceDeps(PsiClass intf, HashSet<T> dependencies, HashMap<T, HashSet<T>> membersToInterfacesMap) {
    HashSet<T> interfaceMethods = myMethodsFromInterfaces.get(intf);

    if(interfaceMethods == null) {
      interfaceMethods = new HashSet<>();
      buildInterfaceMethods(interfaceMethods, intf);
      myMethodsFromInterfaces.put(intf, interfaceMethods);
    }
    for (T method : interfaceMethods) {
      HashSet<T> interfaces = membersToInterfacesMap.get(method);
      if (interfaces == null) {
        interfaces = new HashSet<>();
        membersToInterfacesMap.put(method, interfaces);
      }
      interfaces.add((T)intf);
    }
    dependencies.addAll(interfaceMethods);
  }

  private void buildInterfaceMethods(HashSet<T> interfaceMethods, PsiClass intf) {
    PsiMethod[] methods = intf.getMethods();
    for (PsiMethod method1 : methods) {
      PsiMethod method = myClass.findMethodBySignature(method1, true);
      if (method != null) {
        interfaceMethods.add((T)method);
      }
    }

    PsiReferenceList implementsList = intf.getImplementsList();
    if (implementsList != null) {
      PsiClassType[] implemented = implementsList.getReferencedTypes();
      for (PsiClassType aImplemented : implemented) {
        PsiClass resolved = aImplemented.resolve();
        if (resolved != null) {
          buildInterfaceMethods(interfaceMethods, resolved);
        }
      }
    }

    PsiReferenceList extendsList = intf.getExtendsList();
    if (extendsList != null) {
      PsiClassType[] extended = extendsList.getReferencedTypes();
      for (PsiClassType aExtended : extended) {
        PsiClass ref = aExtended.resolve();
        if (ref != null) {
          buildInterfaceMethods(interfaceMethods, ref);
        }
      }
    }
  }

}
