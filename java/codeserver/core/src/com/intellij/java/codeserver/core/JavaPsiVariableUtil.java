// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.psi.*;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Utility methods related to variables
 */
public final class JavaPsiVariableUtil {

  private static PsiVariable findSameNameSibling(@NotNull PsiVariable variable) {
    PsiElement scope = variable.getParent();
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiVariable psiVariable) {
        if (child.equals(variable)) continue;
        if (Objects.equals(variable.getName(), psiVariable.getName())) {
          return psiVariable;
        }
      }
    }
    return null;
  }

  private static PsiPatternVariable findSamePatternVariableInBranches(@NotNull PsiPatternVariable variable) {
    PsiPattern pattern = variable.getPattern();
    PatternResolveState hint = PatternResolveState.WHEN_TRUE;
    VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
      @Override
      protected boolean check(PsiVariable var, ResolveState state) {
        return var instanceof PsiPatternVariable && super.check(var, state);
      }
    };
    PsiElement lastParent = pattern;
    for (PsiElement parent = lastParent.getParent(); parent != null; lastParent = parent, parent = parent.getParent()) {
      if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiParenthesizedExpression) continue;
      if (parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        hint = hint.invert();
        continue;
      }
      if (parent instanceof PsiPolyadicExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
          PatternResolveState targetHint = PatternResolveState.fromBoolean(tokenType.equals(JavaTokenType.OROR));
          if (hint == targetHint) {
            for (PsiExpression operand : expression.getOperands()) {
              if (operand == lastParent) break;
              operand.processDeclarations(proc, hint.putInto(ResolveState.initial()), null, pattern);
            }
          }
          continue;
        }
      }
      if (parent instanceof PsiConditionalExpression conditional) {
        PsiExpression thenExpression = conditional.getThenExpression();
        if (lastParent == thenExpression) {
          conditional.getCondition()
            .processDeclarations(proc, PatternResolveState.WHEN_FALSE.putInto(ResolveState.initial()), null, pattern);
        }
        else if (lastParent == conditional.getElseExpression()) {
          conditional.getCondition()
            .processDeclarations(proc, PatternResolveState.WHEN_TRUE.putInto(ResolveState.initial()), null, pattern);
          if (thenExpression != null) {
            thenExpression.processDeclarations(proc, hint.putInto(ResolveState.initial()), null, pattern);
          }
        }
      }
      break;
    }
    return proc.size() > 0 ? (PsiPatternVariable)proc.getResult(0) : null;
  }

  /**
   * @param variable variable to check
   * @return a previous declaration of a colliding variable with the same name; null if not found
   */
  public static @Nullable PsiVariable findPreviousVariableDeclaration(@NotNull PsiVariable variable) {
    if (variable instanceof ExternallyDefinedPsiElement || variable.isUnnamed()) return null;
    PsiVariable oldVariable = null;
    PsiElement declarationScope = null;
    if (variable instanceof PsiLocalVariable || variable instanceof PsiPatternVariable ||
        variable instanceof PsiParameter &&
        ((declarationScope = ((PsiParameter)variable).getDeclarationScope()) instanceof PsiCatchSection ||
         declarationScope instanceof PsiForeachStatement ||
         declarationScope instanceof PsiLambdaExpression)) {
      PsiElement scope =
        PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class, PsiResourceList.class);
      VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
        @Override
        protected boolean check(PsiVariable var, ResolveState state) {
          return PsiUtil.isJvmLocalVariable(var) && super.check(var, state);
        }
      };
      PsiIdentifier identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      if (scope instanceof PsiResourceList && proc.size() == 0) {
        scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class);
        PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      }
      if (proc.size() > 0) {
        oldVariable = proc.getResult(0);
      }
      else if (declarationScope instanceof PsiLambdaExpression) {
        oldVariable = findSameNameSibling(variable);
      }
      else if (variable instanceof PsiPatternVariable) {
        oldVariable = findSamePatternVariableInBranches((PsiPatternVariable)variable);
      }
    }
    else if (variable instanceof PsiField field) {
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      PsiField fieldByName = aClass.findFieldByName(variable.getName(), false);
      if (fieldByName != null && fieldByName != field) {
        oldVariable = fieldByName;
      }
      else {
        oldVariable = ContainerUtil.find(aClass.getRecordComponents(), c -> field.getName().equals(c.getName()));
      }
    }
    else {
      oldVariable = findSameNameSibling(variable);
    }
    return oldVariable;
  }
}
