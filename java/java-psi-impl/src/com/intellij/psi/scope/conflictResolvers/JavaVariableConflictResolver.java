// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.scope.conflictResolvers;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaVariableConflictResolver implements PsiConflictResolver{
  @Override
  public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts){
    final int size = conflicts.size();
    if(size == 1){
      return conflicts.get(0);
    }
    if (size == 0) {
      return null;
    }
    final CandidateInfo[] uncheckedResult = conflicts.toArray(new CandidateInfo[size]);
    CandidateInfo currentResult = uncheckedResult[0];

    PsiElement currentElement = currentResult.getElement();
    if(currentElement instanceof PsiField){
      for (int i = 1; i < uncheckedResult.length; i++) {
        final CandidateInfo candidate = uncheckedResult[i];
        final PsiElement otherElement = candidate.getElement();

        if (!(otherElement instanceof PsiField)) {
          if (PsiUtil.isJvmLocalVariable(otherElement)) {
            return candidate;
          }
          else {
            if (!currentResult.isAccessible()) return candidate;
            conflicts.remove(candidate);
            continue;
          }
        }

        final PsiClass newClass = ((PsiField)otherElement).getContainingClass();
        final PsiClass oldClass = ((PsiField)currentElement).getContainingClass();

        final PsiElement scope = currentResult.getCurrentFileResolveScope();
        Boolean oldClassIsInheritor = null;
        if (newClass != null && oldClass != null) {
          if (newClass.isInheritor(oldClass, true)) {
            if (scope instanceof PsiClass &&
                (scope.equals(oldClass) ||
                scope.equals(newClass) ||
                !((PsiClass)scope).isInheritorDeep(oldClass, newClass))) {
              // candidate is better
              conflicts.remove(currentResult);
              currentResult = candidate;
              currentElement = currentResult.getElement();
              continue;
            }
          }
          else if (oldClassIsInheritor = oldClass.isInheritor(newClass, true)) {
            if (!(scope instanceof PsiClass) ||
                scope.equals(oldClass) ||
                scope.equals(newClass) ||
                !((PsiClass)scope).isInheritorDeep(newClass, oldClass)) {
              // candidate is worse
              conflicts.remove(candidate);
              continue;
            }
          }
        }

        if (!candidate.isAccessible()) {
          conflicts.remove(candidate);
          continue;
        }
        if (!currentResult.isAccessible()) {
          conflicts.remove(currentResult);
          currentResult = candidate;
          currentElement = currentResult.getElement();
          continue;
        }

        //This test should go last
        if (otherElement == currentElement) {
          conflicts.remove(candidate);
          continue;
        }

        if (oldClassIsInheritor == null) {
          oldClassIsInheritor = oldClass != null && newClass != null && oldClass.isInheritor(newClass, true);
        }
        if (oldClassIsInheritor && (scope instanceof PsiAnonymousClass ||
                                    !(scope instanceof PsiClass && ((PsiClass)scope).isInheritorDeep(oldClass, newClass)))) {
          // both fields are accessible
          // field in derived hides field in base
          conflicts.remove(candidate);
          continue;
        }
        return null;
      }
    }
    return currentResult;
  }
}
