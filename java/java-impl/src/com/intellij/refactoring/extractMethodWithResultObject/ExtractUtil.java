// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Pavel.Dolgov
 */
class ExtractUtil {
  @Nullable("null means there's an error in the code")
  static PsiElement findOutermostExitedElement(@NotNull PsiElement startLocation, @NotNull PsiElement codeFragmentMember) {
    PsiElement location = startLocation;
    while (true) {
      if (location == null || location instanceof PsiParameterListOwner || location instanceof PsiClassInitializer) {
        return location;
      }
      if (location instanceof PsiBreakStatement) {
        PsiElement exited = ((PsiBreakStatement)location).findExitedElement();
        if (exited == null) {
          return null;
        }
        location = exited;
        continue;
      }
      if (location instanceof PsiContinueStatement) {
        PsiStatement continued = ((PsiContinueStatement)location).findContinuedStatement();
        if (continued instanceof PsiLoopStatement && !ControlFlowUtils.statementMayCompleteNormally(continued)) {
          return ((PsiLoopStatement)continued).getBody();
        }
        if (continued == null) {
          return null;
        }
        location = continued;
        continue;
      }
      if (location instanceof PsiThrowStatement) {
        PsiTryStatement target = findThrowTarget((PsiThrowStatement)location, codeFragmentMember);
        return target != null ? target.getTryBlock() : codeFragmentMember;
      }
      if (location instanceof PsiSwitchLabelStatementBase) {
        location = ((PsiSwitchLabelStatementBase)location).getEnclosingSwitchBlock();
        continue;
      }

      PsiElement parent = location.getParent();
      if (parent instanceof PsiLoopStatement && !ControlFlowUtils.statementMayCompleteNormally((PsiLoopStatement)parent)) {
        return ((PsiLoopStatement)parent).getBody();
      }
      if (parent instanceof PsiCodeBlock) {
        PsiStatement next = PsiTreeUtil.getNextSiblingOfType(location, PsiStatement.class);
        if (next != null) {
          return location;
        }
      }
      location = parent;
    }
  }

  @Nullable
  static PsiTryStatement findThrowTarget(@NotNull PsiThrowStatement statement, @NotNull PsiElement codeFragmentMember) {
    PsiExpression exception = PsiUtil.skipParenthesizedExprDown(statement.getException());
    if (exception == null) {
      return null;
    }
    PsiClassType exactType = null;
    PsiClassType lowerBoundType = null;
    if (exception instanceof PsiNewExpression) {
      PsiType type = exception.getType();
      if (type instanceof PsiClassType) {
        PsiClass resolved = ((PsiClassType)type).resolve();
        if (resolved != null && !(resolved instanceof PsiAnonymousClass)) {
          exactType = lowerBoundType = (PsiClassType)type;
        }
      }
    }
    if (lowerBoundType == null) {
      lowerBoundType = tryCast(exception.getType(), PsiClassType.class);
    }
    if (lowerBoundType == null) {
      return null;
    }
    for (PsiElement element = statement; element != null && element != codeFragmentMember; element = element.getContext()) {
      PsiElement parent = element.getContext();
      if (parent instanceof PsiTryStatement && element == ((PsiTryStatement)parent).getTryBlock()) {
        for (PsiParameter parameter : ((PsiTryStatement)parent).getCatchBlockParameters()) {
          PsiType catchType = parameter.getType();
          if (exactType != null && catchType.isAssignableFrom(exactType)) {
            return ((PsiTryStatement)parent);
          }
          else if (exactType == null && ControlFlowUtil.isCaughtExceptionType(lowerBoundType, catchType)) {
            return ((PsiTryStatement)parent);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  static PsiElement findContext(@NotNull PsiElement from, @NotNull PsiElement topmost, @NotNull PsiElement[] elements) {
    return PsiTreeUtil.findFirstContext(from, false, e -> e == topmost || ArrayUtil.find(elements, e) >= 0);
  }

  @NotNull
  static PsiType getVariableType(@NotNull PsiVariable variable) {
    PsiType type = variable.getType();
    if (type instanceof PsiLambdaParameterType || type instanceof PsiLambdaExpressionType || type instanceof PsiMethodReferenceType) {
      return PsiType.getJavaLangObject(variable.getManager(), GlobalSearchScope.allScope(variable.getProject()));
    }
    return type;
  }
}
