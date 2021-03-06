// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring;

import com.intellij.psi.*;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import junit.framework.Assert;

public class RefactoringTestUtil {
  public static MemberInfo[] findMembers(final PsiClass sourceClass, final MemberDescriptor... membersToFind) {
    MemberInfo[] infos = new MemberInfo[membersToFind.length];
    for (int i = 0; i < membersToFind.length; i++) {
      final Class<? extends PsiMember> clazz = membersToFind[i].myClass;
      final String name = membersToFind[i].myName;
      PsiMember member = null;
      boolean overrides = false;
      PsiReferenceList refList = null;
      if (PsiClass.class.isAssignableFrom(clazz)) {
        member = sourceClass.findInnerClassByName(name, false);
        if (member == null) {
          final PsiClass[] supers = sourceClass.getSupers();
          for (PsiClass superTypeClass : supers) {
            if (superTypeClass.getName().equals(name)) {
              member = superTypeClass;
              overrides = true;
              refList = superTypeClass.isInterface() ? sourceClass.getImplementsList() : sourceClass.getExtendsList();
              break;
            }
          }
        }

      } else if (PsiMethod.class.isAssignableFrom(clazz)) {
        final PsiMethod[] methods = sourceClass.findMethodsByName(name, false);
        Assert.assertEquals(1, methods.length);
        member = methods[0];
      } else if (PsiField.class.isAssignableFrom(clazz)) {
        member = sourceClass.findFieldByName(name, false);
      }

      Assert.assertNotNull(member);
      infos[i] = new MemberInfo(member, overrides, refList);
      infos[i].setToAbstract(membersToFind[i].myAbstract);
    }
    return infos;
  }

  public static class MemberDescriptor {
    private final String myName;
    private final Class<? extends PsiMember> myClass;
    private final boolean myAbstract;

    public MemberDescriptor(String name, Class<? extends PsiMember> aClass, boolean isAbstract) {
      myName = name;
      myClass = aClass;
      myAbstract = isAbstract;
    }


    public MemberDescriptor(String name, Class<? extends PsiMember> aClass) {
      this(name, aClass, false);
    }
  }
}