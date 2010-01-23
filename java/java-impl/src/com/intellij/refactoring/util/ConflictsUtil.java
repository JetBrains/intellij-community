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

/**
 * created at Oct 8, 2001
 * @author Jeka
 */
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConflictsUtil {
  private ConflictsUtil() {
  }

  @NotNull
  public static PsiElement getContainer(PsiElement place) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiMember && !(parent instanceof PsiTypeParameter))
        return parent;
      if (parent instanceof PsiFile) return parent;
      parent = parent.getParent();
    }
  }

  public static void checkMethodConflicts(@Nullable PsiClass aClass,
                                          PsiMethod refactoredMethod,
                                          PsiMethod prototype,
                                          final MultiMap<PsiElement,String> conflicts) {
    if (prototype == null) return;

    PsiMethod method = aClass != null ? aClass.findMethodBySignature(prototype, true) : null;

    if (method != null && method != refactoredMethod) {
      if (aClass.equals(method.getContainingClass())) {
        final String classDescr = aClass instanceof PsiAnonymousClass ?
                                  RefactoringBundle.message("current.class") :
                                  RefactoringUIUtil.getDescription(aClass, false);
        conflicts.putValue(method, RefactoringBundle.message("method.0.is.already.defined.in.the.1",
                                                getMethodPrototypeString(prototype),
                                                classDescr));
      }
      else { // method somewhere in base class
        if (JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(method, aClass, null)) {
          String protoMethodInfo = getMethodPrototypeString(prototype);
          String className = CommonRefactoringUtil.htmlEmphasize(UsageViewUtil.getDescriptiveName(method.getContainingClass()));
          if (PsiUtil.getAccessLevel(prototype.getModifierList()) >= PsiUtil.getAccessLevel(method.getModifierList()) ) {
            boolean isMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
            boolean isMyMethodAbstract = refactoredMethod != null && refactoredMethod.hasModifierProperty(PsiModifier.ABSTRACT);
            final String conflict = isMethodAbstract != isMyMethodAbstract ?
                                    RefactoringBundle.message("method.0.will.implement.method.of.the.base.class", protoMethodInfo, className) :
                                    RefactoringBundle.message("method.0.will.override.a.method.of.the.base.class", protoMethodInfo, className);
            conflicts.putValue(method, conflict);
          }
          else { // prototype is private, will be compile-error
            conflicts.putValue(method, RefactoringBundle.message("method.0.will.hide.method.of.the.base.class",
                                                    protoMethodInfo, className));
          }
        }
      }
    }
  }

  private static String getMethodPrototypeString(final PsiMethod prototype) {
    return PsiFormatUtil.formatMethod(
      prototype,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
  }

  public static void checkFieldConflicts(@Nullable PsiClass aClass, String newName, final MultiMap<PsiElement, String> conflicts) {
    PsiField existingField = aClass != null ? aClass.findFieldByName(newName, true) : null;
    if (existingField != null) {
      if (aClass.equals(existingField.getContainingClass())) {
        String className = aClass instanceof PsiAnonymousClass ?
                           RefactoringBundle.message("current.class") :
                           RefactoringUIUtil.getDescription(aClass, false);
        final String conflict = RefactoringBundle.message("field.0.is.already.defined.in.the.1",
                                                          existingField.getName(), className);
        conflicts.putValue(existingField, conflict);
      }
      else { // method somewhere in base class
        if (!existingField.hasModifierProperty(PsiModifier.PRIVATE)) {
          String fieldInfo = PsiFormatUtil.formatVariable(existingField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER, PsiSubstitutor.EMPTY);
          String className = RefactoringUIUtil.getDescription(existingField.getContainingClass(), false);
          final String descr = RefactoringBundle.message("field.0.will.hide.field.1.of.the.base.class",
                                                         newName, fieldInfo, className);
          conflicts.putValue(existingField, descr);
        }
      }
    }
  }
}
