// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.util;

import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConflictsUtil {
  private ConflictsUtil() {
  }

  @NotNull
  public static PsiElement getContainer(PsiElement place) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiMember && !(parent instanceof PsiTypeParameter)) {
        return parent;
      }
      if (parent instanceof PsiFile) {
        PsiElement host = FileContextUtil.getFileContext((PsiFile)parent);
        if (host == null) {
          return parent;
        }
        parent = host;
      }
      parent = parent.getParent();
    }
  }

  public static void checkMethodConflicts(@Nullable PsiClass aClass,
                                          @Nullable PsiMethod refactoredMethod,
                                          final PsiMethod prototype,
                                          final MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflicts) {
    if (prototype == null) return;
    String protoMethodInfo = getMethodPrototypeString(prototype);

    PsiMethod method = aClass != null ? aClass.findMethodBySignature(prototype, true) : null;
    if (method == null && aClass != null) {
      final MethodSignature signature = prototype.getSignature(PsiSubstitutor.EMPTY);
      for (PsiMethod classMethod : aClass.getMethods()) {
        if (MethodSignatureUtil.areSignaturesErasureEqual(signature, classMethod.getSignature(PsiSubstitutor.EMPTY))) {
          method = classMethod;
          protoMethodInfo = "with same erasure";
          break;
        }
      }
    }

    if (method != null && method != refactoredMethod && !isStaticInterfaceMethods(aClass, refactoredMethod, method)
        && (!(method instanceof LightRecordCanonicalConstructor) || !(refactoredMethod instanceof LightRecordCanonicalConstructor))) {
      if (aClass.equals(method.getContainingClass())) {
        final String classDescr = aClass instanceof PsiAnonymousClass ?
                                  JavaRefactoringBundle.message("current.class") :
                                  RefactoringUIUtil.getDescription(aClass, false);
        conflicts.putValue(method, RefactoringBundle.message("method.0.is.already.defined.in.the.1",
                                                protoMethodInfo,
                                                classDescr));
      }
      else { // method somewhere in base class
        if (JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(method, aClass, null)) {
          String className = CommonRefactoringUtil.htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(method.getContainingClass()));
          if (PsiUtil.getAccessLevel(prototype.getModifierList()) >= PsiUtil.getAccessLevel(method.getModifierList()) ) {
            boolean isMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
            boolean isMyMethodAbstract = refactoredMethod != null && refactoredMethod.hasModifierProperty(PsiModifier.ABSTRACT);
            final String conflict = isMethodAbstract != isMyMethodAbstract ?
                                    JavaRefactoringBundle.message("method.0.will.implement.method.of.the.base.class", protoMethodInfo, className) :
                                    JavaRefactoringBundle.message("method.0.will.override.a.method.of.the.base.class", protoMethodInfo, className);
            conflicts.putValue(method, conflict);
          }
          else { // prototype is private, will be compile-error
            conflicts.putValue(method, JavaRefactoringBundle.message("method.0.will.hide.method.of.the.base.class",
                                                    protoMethodInfo, className));
          }
        }
      }
    }
    if (aClass != null && prototype.hasModifierProperty(PsiModifier.PRIVATE)) {
      ClassInheritorsSearch.search(aClass).forEach(aClass1 -> {
        final PsiMethod[] methods = aClass1.findMethodsBySignature(prototype, false);
        for (PsiMethod method1 : methods) {
          conflicts.putValue(method1, JavaBundle.message("conflict.message.method.will.override.method.base.class",
                                                         RefactoringUIUtil.getDescription(method1, true),
                                                         RefactoringUIUtil.getDescription(aClass, false)));
        }
        return true;
      });
    }
  }

  private static boolean isStaticInterfaceMethods(PsiClass aClass, PsiMethod refactoredMethod, PsiMethod method) {
    return aClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC) &&
           refactoredMethod != null && refactoredMethod.hasModifierProperty(PsiModifier.STATIC);
  }

  private static String getMethodPrototypeString(final PsiMethod prototype) {
    return PsiFormatUtil.formatMethod(
      prototype,
      PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
      PsiFormatUtilBase.SHOW_TYPE
    );
  }

  public static void checkFieldConflicts(@Nullable PsiClass aClass, String newName, final MultiMap<PsiElement, String> conflicts) {
    PsiField existingField = aClass != null ? aClass.findFieldByName(newName, true) : null;
    if (existingField != null) {
      if (aClass.equals(existingField.getContainingClass())) {
        String className = aClass instanceof PsiAnonymousClass ?
                           JavaRefactoringBundle.message("current.class") :
                           RefactoringUIUtil.getDescription(aClass, false);
        final String conflict = JavaRefactoringBundle.message("field.0.is.already.defined.in.the.1",
                                                          existingField.getName(), className);
        conflicts.putValue(existingField, conflict);
      }
      else { // method somewhere in base class
        if (!existingField.hasModifierProperty(PsiModifier.PRIVATE)) {
          String fieldInfo = PsiFormatUtil.formatVariable(existingField, PsiFormatUtilBase.SHOW_NAME |
                                                                         PsiFormatUtilBase.SHOW_TYPE |
                                                                         PsiFormatUtilBase.TYPE_AFTER, PsiSubstitutor.EMPTY);
          String className = RefactoringUIUtil.getDescription(existingField.getContainingClass(), false);
          final String descr = JavaRefactoringBundle.message("field.0.will.hide.field.1.of.the.base.class",
                                                         newName, fieldInfo, className);
          conflicts.putValue(existingField, descr);
        }
      }
    }
  }
}
