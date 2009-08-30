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
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ConflictsUtil {
  private ConflictsUtil() {
  }

  @Nullable
  public static PsiMember getContainer(PsiElement place) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiMember && !(parent instanceof PsiTypeParameter))
        return (PsiMember)parent;
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  public static void checkMethodConflicts(@Nullable PsiClass aClass,
                                          PsiMethod refactoredMethod,
                                          PsiMethod prototype,
                                          final Collection<String> conflicts) {
    if (prototype == null) return;

    PsiMethod method = aClass != null ? aClass.findMethodBySignature(prototype, true) : null;

    if (method != null && method != refactoredMethod) {
      if (aClass.equals(method.getContainingClass())) {
        final String classDescr = aClass instanceof PsiAnonymousClass ?
                                  RefactoringBundle.message("current.class") :
                                  RefactoringUIUtil.getDescription(aClass, false);
        conflicts.add(RefactoringBundle.message("method.0.is.already.defined.in.the.1",
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
            conflicts.add(conflict);
          }
          else { // prototype is private, will be compile-error
            conflicts.add(RefactoringBundle.message("method.0.will.hide.method.of.the.base.class",
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

  public static void checkFieldConflicts(@Nullable PsiClass aClass, String newName, final Collection<String> conflicts) {
    PsiField existingField = aClass != null ? aClass.findFieldByName(newName, true) : null;
    if (existingField != null) {
      if (aClass.equals(existingField.getContainingClass())) {
        String className = aClass instanceof PsiAnonymousClass ?
                           RefactoringBundle.message("current.class") :
                           RefactoringUIUtil.getDescription(aClass, false);
        final String conflict = RefactoringBundle.message("field.0.is.already.defined.in.the.1",
                                                          existingField.getName(), className);
        conflicts.add(conflict);
      }
      else { // method somewhere in base class
        if (!existingField.hasModifierProperty(PsiModifier.PRIVATE)) {
          String fieldInfo = PsiFormatUtil.formatVariable(existingField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER, PsiSubstitutor.EMPTY);
          String className = RefactoringUIUtil.getDescription(existingField.getContainingClass(), false);
          final String descr = RefactoringBundle.message("field.0.will.hide.field.1.of.the.base.class",
                                                         newName, fieldInfo, className);
          conflicts.add(descr);
        }
      }
    }
  }
}
