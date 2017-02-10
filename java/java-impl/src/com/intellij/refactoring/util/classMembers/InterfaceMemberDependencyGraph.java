/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberDependencyGraph;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.util.containers.HashMap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class InterfaceMemberDependencyGraph<T extends PsiMember, M extends MemberInfoBase<T>> implements MemberDependencyGraph<T, M> {
  protected HashSet<T> myInterfaceDependencies;
  protected HashMap<T,HashSet<T>> myMembersToInterfacesMap = new HashMap<>();
  protected HashSet<PsiClass> myImplementedInterfaces;
  protected HashMap<PsiClass,HashSet<T>> myMethodsFromInterfaces;
  protected PsiClass myClass;

  public InterfaceMemberDependencyGraph(PsiClass aClass) {
    myClass = aClass;
    myImplementedInterfaces = new HashSet<>();
    myMethodsFromInterfaces = new HashMap<>();
  }

  @Override
  public void memberChanged(M memberInfo) {
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
  public Set<? extends T> getDependent() {
    if(myInterfaceDependencies == null) {
      myInterfaceDependencies = new HashSet<>();
      myMembersToInterfacesMap = new HashMap<>();
      for (final PsiClass implementedInterface : myImplementedInterfaces) {
        addInterfaceDeps(implementedInterface);
      }
    }
    return myInterfaceDependencies;
  }

  @Override
  public Set<? extends T> getDependenciesOf(PsiMember member) {
    final Set dependent = getDependent();
    if(dependent.contains(member)) return myMembersToInterfacesMap.get(member);
    return null;
  }

  public String getElementTooltip(PsiMember member) {
    final Set<? extends PsiMember> dependencies = getDependenciesOf(member);
    if(dependencies == null || dependencies.size() == 0) return null;
    StringBuilder buffer = new StringBuilder();
    buffer.append(RefactoringBundle.message("interface.member.dependency.required.by.interfaces", dependencies.size()));
    buffer.append(" ");
    for (Iterator<? extends PsiMember> iterator = dependencies.iterator(); iterator.hasNext();) {
      PsiClass aClass = (PsiClass) iterator.next();
      buffer.append(aClass.getName());
      if(iterator.hasNext()) {
        buffer.append(", ");
      }
    }
    return buffer.toString();
  }

  protected void addInterfaceDeps(PsiClass intf) {
    HashSet<T> interfaceMethods = myMethodsFromInterfaces.get(intf);

    if(interfaceMethods == null) {
      interfaceMethods = new HashSet<>();
      buildInterfaceMethods(interfaceMethods, intf);
      myMethodsFromInterfaces.put(intf, interfaceMethods);
    }
    for (T method : interfaceMethods) {
      HashSet<T> interfaces = myMembersToInterfacesMap.get(method);
      if (interfaces == null) {
        interfaces = new HashSet<>();
        myMembersToInterfacesMap.put(method, interfaces);
      }
      interfaces.add((T)intf);
    }
    myInterfaceDependencies.addAll(interfaceMethods);
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
