// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MemberInfo extends MemberInfoBase<PsiMember> {
  private final PsiReferenceList mySourceReferenceList;

  public MemberInfo(PsiMember member) {
    this(member, false, null);
  }
  public MemberInfo(PsiMember member, boolean isSuperClass, PsiReferenceList sourceReferenceList) {
    super(member);
    LOG.assertTrue(member.isValid());
    mySourceReferenceList = sourceReferenceList;
    if (member instanceof PsiMethod method) {
      displayName = PsiFormatUtil.formatMethod(method,
                                               PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
                                               PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER
      );
      PsiMethod[] superMethods = method.findSuperMethods();
      if (superMethods.length > 0) {
        overrides = !superMethods[0].hasModifierProperty(PsiModifier.ABSTRACT);
      }
      else {
        overrides = null;
      }
      isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    }
    else if (member instanceof PsiField field) {
      displayName = PsiFormatUtil.formatVariable(
              field,
              PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER,
              PsiSubstitutor.EMPTY);
      isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      overrides = null;
    }
    else if (member instanceof PsiClass aClass) {
      if(isSuperClass) {
        if (aClass.isInterface()) {
          displayName = RefactoringBundle.message("member.info.implements.0", aClass.getName());
          overrides = Boolean.FALSE;
        }
        else {
          displayName = RefactoringBundle.message("member.info.extends.0", aClass.getName());
          overrides = Boolean.TRUE;
        }
      }
      else {
        displayName = aClass.getName();
        overrides = null;
      }
      isStatic = aClass.hasModifierProperty(PsiModifier.STATIC);
    }
    else if (member instanceof PsiClassInitializer) {
      isStatic = member.hasModifierProperty(PsiModifier.STATIC);
      overrides = null;
      displayName = isStatic ? "static {...}" : "{...}";
    }
    else {
      LOG.assertTrue(false);
      isStatic = false;
      displayName = "";
      overrides = null;
    }
  }

  public PsiReferenceList getSourceReferenceList() {
    return mySourceReferenceList;
  }

  public static List<MemberInfo> extractClassMembers(PsiClass subclass, Filter<PsiMember> filter, boolean extractInterfacesDeep) {
    List<MemberInfo> members = new ArrayList<>();
    extractClassMembers(subclass, members, filter, extractInterfacesDeep);
    return members;
  }

  public static void extractClassMembers(PsiClass subclass, List<MemberInfo> result, Filter<PsiMember> filter, final boolean extractInterfacesDeep) {
    if (extractInterfacesDeep) {
      extractSuperInterfaces(subclass, filter, result, new HashSet<>());
    }
    else {
      PsiClass[] interfaces = subclass.getInterfaces();
      PsiReferenceList sourceRefList = subclass.isInterface() ? subclass.getExtendsList() : subclass.getImplementsList();
      for (PsiClass anInterface : interfaces) {
        if (filter.includeMember(anInterface)) {
          result.add(new MemberInfo(anInterface, true, sourceRefList));
        }
      }
    }

    PsiClass[] innerClasses = subclass.getInnerClasses();
    for (PsiClass innerClass : innerClasses) {
      if (filter.includeMember(innerClass)) {
        result.add(new MemberInfo(innerClass));
      }
    }
    PsiMethod[] methods = subclass.getMethods();
    for (PsiMethod method : methods) {
      if (!method.isConstructor() && filter.includeMember(method)) {
        result.add(new MemberInfo(method));
      }
    }
    PsiField[] fields = subclass.getFields();
    for (final PsiField field : fields) {
      if (filter.includeMember(field)) {
        result.add(new MemberInfo(field));
      }
    }

    for (PsiClassInitializer initializer : subclass.getInitializers()) {
      if (filter.includeMember(initializer)) {
        result.add(new MemberInfo(initializer));
      }
    }
  }

  private static void extractSuperInterfaces(final PsiClass subclass,
                                             final Filter<PsiMember> filter,
                                             final List<MemberInfo> result,
                                             Set<PsiClass> processed) {
    if (!processed.contains(subclass)) {
      processed.add(subclass);
      extractSuperInterfacesFromReferenceList(subclass.getExtendsList(), filter, result, processed);
      extractSuperInterfacesFromReferenceList(subclass.getImplementsList(), filter, result, processed);
    }
  }

  private static void extractSuperInterfacesFromReferenceList(final PsiReferenceList referenceList,
                                                              final Filter<PsiMember> filter,
                                                              final List<MemberInfo> result,
                                                              final Set<PsiClass> processed) {
    if (referenceList != null) {
      final PsiClassType[] extendsListTypes = referenceList.getReferencedTypes();
      for (PsiClassType extendsListType : extendsListTypes) {
        final PsiClass aSuper = extendsListType.resolve();
        if (aSuper != null) {
          if (aSuper.isInterface()) {
            if (filter.includeMember(aSuper)) {
              result.add(new MemberInfo(aSuper, true, referenceList));
            }
          }
          else {
            extractSuperInterfaces(aSuper, filter, result, processed);
          }
        }
      }
    }
  }
}
