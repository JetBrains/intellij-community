/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.classMembers.AbstractMemberInfoStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


public class MemberInfoStorage extends AbstractMemberInfoStorage<PsiMember, PsiClass, MemberInfo> {

  public MemberInfoStorage(PsiClass aClass, MemberInfo.Filter<PsiMember> memberInfoFilter) {
    super(aClass, memberInfoFilter);
  }

  @Override
  protected boolean isInheritor(PsiClass baseClass, PsiClass aClass) {
    return aClass.isInheritor(baseClass, true);
  }

  @Override
  protected void extractClassMembers(PsiClass aClass, ArrayList<MemberInfo> temp) {
    MemberInfo.extractClassMembers(aClass, temp, myFilter, false);
  }

  @Override
  protected boolean memberConflict(PsiMember member1, PsiMember member) {
    if(member instanceof PsiMethod && member1 instanceof PsiMethod) {
      return MethodSignatureUtil.areSignaturesEqual((PsiMethod) member, (PsiMethod) member1);
    }
    else if(member instanceof PsiField && member1 instanceof PsiField
            || member instanceof PsiClass && member1 instanceof PsiClass) {
      return member.getName().equals(member1.getName());
    }
    return false;
  }


  @Override
  protected void buildSubClassesMap(PsiClass aClass) {
    buildSubClassesMap(aClass, new HashSet<>());
  }

  private void buildSubClassesMap(PsiClass aClass, Set<PsiClass> visited) {
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList != null) {
      buildSubClassesMapForList(aClass, visited, extendsList.getReferencedTypes());
    }
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList != null) {
      buildSubClassesMapForList(aClass, visited, implementsList.getReferencedTypes());
    }

    if (aClass instanceof PsiAnonymousClass) {
      buildSubClassesMapForList(aClass, visited, ((PsiAnonymousClass)aClass).getBaseClassType());
    }
  }

  private void buildSubClassesMapForList(final PsiClass aClass,
                                         final Set<PsiClass> processed,
                                         final PsiClassType... classesList) {
    for (PsiClassType element : classesList) {
      PsiClass resolved = element.resolve();
      if (resolved != null && processed.add(resolved)) {
        getSubclasses(resolved).add(aClass);
        buildSubClassesMap(resolved, processed);
      }
    }
  }
}
