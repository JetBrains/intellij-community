// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class to support the code highlighting in the incomplete dependencies state
 */
final class IncompleteModelUtil {
  /**
   * Checks if all the transitive superclasses and superinterfaces of a given {@link PsiClass} is resolved.
   *
   * @param psiClass the PsiClass to check
   * @return {@code true} if the complete class hierarchy is resolved, {@code false} otherwise
   */
  static boolean isHierarchyResolved(@NotNull PsiClass psiClass) {
    Set<PsiClass> processed = new HashSet<>();
    Deque<PsiClass> stack = new ArrayDeque<>();
    stack.push(psiClass);
    while (!stack.isEmpty()) {
      PsiClass currentClass = stack.pop();
      if (!processed.add(currentClass)) continue;
      for (PsiClassType type : currentClass.getSuperTypes()) {
        PsiClass resolved = type.resolve();
        if (resolved == null) return false;
        stack.push(resolved);
      }
    }
    return true;
  }

  /**
   * Checks if the given PsiType represents a class type which is unresolved or has an unresolved supertype.
   *
   * @param psiType the PsiType to check
   * @return true if the PsiType is a class type that is not completely resolved
   */
  @Contract("null -> false")
  static boolean isUnresolvedClassType(@Nullable PsiType psiType) {
    Deque<PsiType> stack = new ArrayDeque<>();
    if (psiType != null) {
      stack.push(psiType);
    }
    while (!stack.isEmpty()) {
      PsiType currentType = stack.pop();
      if (currentType instanceof PsiLambdaParameterType) {
        return true;
      }
      if (currentType instanceof PsiIntersectionType intersectionType) {
        Collections.addAll(stack, intersectionType.getConjuncts());
      }
      if (currentType instanceof PsiDisjunctionType disjunctionType) {
        stack.addAll(disjunctionType.getDisjunctions());
      }
      if (currentType instanceof PsiClassType classType) {
        PsiClass resolved = classType.resolve();
        if (resolved == null || !isHierarchyResolved(resolved)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @param psiType type to check
   * @return true if the type has any unresolved component. Unlike {@link #isUnresolvedClassType(PsiType)}, this method returns
   * true, e.g., for {@code List<Foo>} where {@code List} is resolved but {@code Foo} is not.
   */
  @Contract("null -> false")
  static boolean hasUnresolvedComponent(@Nullable PsiType psiType) {
    if (psiType == null) return false;
    PsiType type = psiType.getDeepComponentType();
    if (isUnresolvedClassType(psiType)) {
      return true;
    }
    if (type instanceof PsiClassType classType) {
      for (PsiType parameter : classType.getParameters()) {
        if (hasUnresolvedComponent(parameter)) {
          return true;
        }
      }
    }
    if (type instanceof PsiWildcardType wildcardType) {
      return hasUnresolvedComponent(wildcardType.getBound());
    }
    if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
      return hasUnresolvedComponent(capturedWildcardType.getLowerBound()) ||
             hasUnresolvedComponent(capturedWildcardType.getUpperBound());
    }
    return false;
  }

  /**
   * @param targetType type to check the convertibility to
   * @param expression expression to check
   * @return true, if the result of the expression could be potentially convertible to the targetType, taking into account
   * the incomplete project model.
   */
  static boolean isPotentiallyConvertible(@Nullable PsiType targetType, @NotNull PsiExpression expression) {
    PsiType rightType = expression.getType();
    return isPotentiallyConvertible(targetType, expression, rightType, expression);
  }

  /**
   * @param leftType first type to check
   * @param rightType second type to check
   * @param context context PSI element
   * @return true, if one of the types could be potentially convertible to another type, taking into account
   * the incomplete project model.
   */
  static boolean isPotentiallyConvertible(@Nullable PsiType leftType, @NotNull PsiType rightType, @NotNull PsiElement context) {
    return isPotentiallyConvertible(leftType, null, rightType, context);
  }

  private static boolean isPotentiallyConvertible(@Nullable PsiType leftType, @Nullable PsiExpression rightExpr, @Nullable PsiType rightType, @NotNull PsiElement context) {
    boolean pendingLeft = leftType == null || hasUnresolvedComponent(leftType);
    boolean pendingRight =
      rightType == null || (rightExpr == null ? hasUnresolvedComponent(rightType) : mayHaveUnknownTypeDueToPendingReference(rightExpr));
    if (pendingLeft && pendingRight) return true;
    if (!pendingLeft && !pendingRight) {
      return leftType.isConvertibleFrom(rightType);
    }
    if (pendingLeft && leftType != null) {
      if (rightType instanceof PsiPrimitiveType primitiveType && primitiveType.getBoxedType(context) != null) return false;
      PsiClass rightTypeClass = PsiUtil.resolveClassInClassTypeOnly(rightType);
      if (rightTypeClass != null &&
          rightTypeClass.hasModifierProperty(PsiModifier.FINAL) &&
          rightTypeClass.getTypeParameters().length == 0) {
        return false;
      }
    }
    if (pendingRight && rightType != null) {
      if (leftType instanceof PsiPrimitiveType primitiveType && primitiveType.getBoxedType(context) != null) return false;
      PsiClass leftTypeClass = PsiUtil.resolveClassInClassTypeOnly(leftType);
      if (leftTypeClass != null &&
          leftTypeClass.hasModifierProperty(PsiModifier.FINAL) &&
          leftTypeClass.getTypeParameters().length == 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if the project is in incomplete dependencies state.
   *
   * @param context the context element to find the project for
   * @return {@code true} if the project is in incomplete dependencies state, {@code false} otherwise
   */
  static boolean isIncompleteModel(@NotNull PsiElement context) {
    return !context.getProject().getService(IncompleteDependenciesService.class).getState().isComplete();
  }

  /**
   * @param expression expression to check
   * @return true if the expression type could be unknown due to a pending reference which affects it.
   */
  static boolean mayHaveUnknownTypeDueToPendingReference(@NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    if (type != null) {
      return hasUnresolvedComponent(type);
    }
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression call && canBePendingReference(call.getMethodExpression())) {
      return true;
    }
    if (expression instanceof PsiReferenceExpression ref && canBePendingReference(ref)) {
      return true;
    }
    return false;
  }

  /**
   * @param ref reference to check
   * @return true if the reference can be pending. A pending reference is an unresolved reference that can be potentially resolved
   * once the project dependencies are properly resolved. Not every reference can be pending. E.g., an unresolved method inside
   * the fully resolved class cannot be pending.
   */
  static boolean canBePendingReference(@NotNull PsiJavaCodeReferenceElement ref) {
    if (ref instanceof PsiReferenceExpression refExpr) {
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null) {
        PsiClass psiClass = ClassUtils.getContainingClass(ref);
        while (psiClass != null) {
          if (isHierarchyResolved(psiClass)) return true;
          psiClass = ClassUtils.getContainingClass(psiClass);
        }
        boolean call = ref.getParent() instanceof PsiMethodCallExpression;
        PsiImportList list = ((PsiJavaFile)ref.getContainingFile()).getImportList();
        if (list != null) {
          for (PsiImportStatementBase statement : list.getAllImportStatements()) {
            if (statement instanceof PsiImportStaticStatement staticImport && staticImport.resolveTargetClass() != null) continue;
            if (!statement.isOnDemand()) {
              PsiJavaCodeReferenceElement reference = statement.getImportReference();
              if (reference == null) continue;
              String name = reference.getReferenceName();
              if (name == null || !name.equals(ref.getReferenceName())) continue;
            }
            // Unqualified method call cannot be imported using non-static import
            if (statement instanceof PsiImportStaticStatement || !call) {
              return true;
            }
          }
        }
        return false;
      }
      if (qualifier instanceof PsiReferenceExpression qualifierRef) {
        PsiElement qualifierTarget = qualifierRef.resolve();
        if (qualifierTarget == null && canBePendingReference(qualifierRef)) {
          return true;
        }
        if (qualifierTarget instanceof PsiClass cls && isHierarchyResolved(cls)) {
          return false;
        } 
      }
      PsiType qualifierType = qualifier.getType();
      if (isUnresolvedClassType(qualifierType) || qualifierType == null && mayHaveUnknownTypeDueToPendingReference(qualifier)) {
        return true;
      }
    }
    else {
      return true;
    }
    return false;
  }

  /**
   * @param elementToHighlight element to attach the highlighting
   * @return HighlightInfo builder that adds a pending reference highlight
   */
  static HighlightInfo.@NotNull Builder getPendingReferenceHighlightInfo(@NotNull PsiElement elementToHighlight) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.PENDING_REFERENCE).range(elementToHighlight)
      .descriptionAndTooltip(JavaErrorBundle.message("incomplete.project.state.pending.reference"));
  }
}
