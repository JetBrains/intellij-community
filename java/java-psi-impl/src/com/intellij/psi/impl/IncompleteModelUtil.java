// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class to support the code highlighting in the incomplete dependencies state
 */
@ApiStatus.Internal
public final class IncompleteModelUtil {

  /**
   * Checks if all the transitive superclasses and superinterfaces of a given {@link PsiClass} is resolved.
   *
   * @param psiClass the PsiClass to check
   * @return {@code true} if the complete class hierarchy is resolved, {@code false} otherwise
   */
  public static boolean isHierarchyResolved(@NotNull PsiClass psiClass) {
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
  public static boolean isUnresolvedClassType(@Nullable PsiType psiType) {
    Deque<PsiType> stack = new ArrayDeque<>();
    if (psiType != null) {
      stack.push(psiType);
    }
    while (!stack.isEmpty()) {
      PsiType currentType = stack.pop();
      if (currentType instanceof PsiLambdaParameterType) {
        return true;
      }
      if (currentType instanceof PsiIntersectionType) {
        Collections.addAll(stack, ((PsiIntersectionType)currentType).getConjuncts());
      }
      if (currentType instanceof PsiDisjunctionType) {
        stack.addAll(((PsiDisjunctionType)currentType).getDisjunctions());
      }
      if (currentType instanceof PsiClassType) {
        PsiClass resolved = ((PsiClassType)currentType).resolve();
        if (resolved == null || !isHierarchyResolved(resolved)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @param psiType type to check
   * @return true if the type has any unresolved component. Unlike {@link IncompleteModelUtil#isUnresolvedClassType(PsiType)}, this method returns
   * true, e.g., for {@code List<Foo>} where {@code List} is resolved but {@code Foo} is not.
   */
  @Contract("null -> false")
  public static boolean hasUnresolvedComponent(@Nullable PsiType psiType) {
    return hasUnresolvedComponentRecursively(psiType, new HashSet<>());
  }

  private static boolean hasUnresolvedComponentRecursively(@Nullable PsiType psiType, @NotNull HashSet<PsiClass> visited) {
    if (psiType == null) return false;
    PsiType type = psiType.getDeepComponentType();
    if (isUnresolvedClassType(psiType)) {
      return true;
    }
    if (type instanceof PsiClassType) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (psiClass != null && !visited.add(psiClass)) {
        return false;
      }
      for (PsiType parameter : ((PsiClassType)type).getParameters()) {
        if (hasUnresolvedComponentRecursively(parameter, visited)) {
          return true;
        }
      }
    }
    if (type instanceof PsiWildcardType) {
      return hasUnresolvedComponentRecursively(((PsiWildcardType)type).getBound(), visited);
    }
    if (type instanceof PsiCapturedWildcardType) {
      return hasUnresolvedComponentRecursively(((PsiCapturedWildcardType)type).getLowerBound(), visited) ||
             hasUnresolvedComponentRecursively(((PsiCapturedWildcardType)type).getUpperBound(), visited);
    }
    return false;
  }

  /**
   * Checks if the project is in incomplete dependencies state.
   *
   * @param context the context element to find the project for
   * @return {@code true} if the project is in incomplete dependencies state, {@code false} otherwise
   */
  public static boolean isIncompleteModel(@NotNull PsiElement context) {
    return !context.getProject().getService(IncompleteDependenciesService.class).getState().isComplete();
  }

  /**
   * @param targetType type to check the convertibility to
   * @param expression expression to check
   * @return true, if the result of the expression could be potentially convertible to the targetType, taking into account
   * the incomplete project model.
   */
  public static boolean isPotentiallyConvertible(@Nullable PsiType targetType, @NotNull PsiExpression expression) {
    PsiType rightType = expression.getType();
    return isPotentiallyConvertible(targetType, expression, rightType, expression);
  }

  /**
   * @param leftType  first type to check
   * @param rightType second type to check
   * @param context   context PSI element
   * @return true, if one of the types could be potentially convertible to another type, taking into account
   * the incomplete project model.
   */
  public static boolean isPotentiallyConvertible(@Nullable PsiType leftType, @NotNull PsiType rightType, @NotNull PsiElement context) {
    return isPotentiallyConvertible(leftType, null, rightType, context);
  }

  private static boolean isPotentiallyConvertible(@Nullable PsiType leftType,
                                                  @Nullable PsiExpression rightExpr,
                                                  @Nullable PsiType rightType,
                                                  @NotNull PsiElement context) {
    if (leftType instanceof PsiLambdaParameterType || rightType instanceof PsiLambdaParameterType) return true;
    boolean pendingLeft = leftType == null || hasUnresolvedComponent(leftType);
    boolean pendingRight =
      rightType == null || (rightExpr == null ? hasUnresolvedComponent(rightType) : mayHaveUnknownTypeDueToPendingReference(rightExpr));
    if (pendingLeft && pendingRight) return true;
    if (!pendingLeft && !pendingRight) {
      return leftType.isConvertibleFrom(rightType);
    }
    if (pendingLeft && isStrictlyInconvertible(leftType, rightType, context)) {
      return false;
    }
    if (pendingRight && isStrictlyInconvertible(rightType, leftType, context)) {
      return false;
    }
    return true;
  }

  private static boolean isStrictlyInconvertible(@Nullable PsiType pendingType,
                                                 @Nullable PsiType resolvedType,
                                                 @NotNull PsiElement context) {
    if (pendingType != null) {
      if (resolvedType instanceof PsiPrimitiveType && ((PsiPrimitiveType)resolvedType).getBoxedType(context) != null) return true;
      PsiClass rightTypeClass = PsiUtil.resolveClassInClassTypeOnly(resolvedType);
      if (rightTypeClass != null &&
          rightTypeClass.hasModifierProperty(PsiModifier.FINAL) &&
          rightTypeClass.getTypeParameters().length == 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param expression expression to check
   * @return true if the expression type could be unknown due to a pending reference which affects it.
   */
  public static boolean mayHaveUnknownTypeDueToPendingReference(@NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    if (type != null && !(type instanceof PsiMethodReferenceType)) {
      return hasUnresolvedComponent(type);
    }
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression &&
        canBePendingReference(((PsiMethodCallExpression)expression).getMethodExpression())) {
      return true;
    }
    if (expression instanceof PsiReferenceExpression && canBePendingReference((PsiReferenceExpression)expression)) {
      return true;
    }
    if (expression instanceof PsiArrayAccessExpression) {
      return mayHaveUnknownTypeDueToPendingReference(((PsiArrayAccessExpression)expression).getArrayExpression());
    }
    return false;
  }

  private static boolean mayHaveNullTypeDueToPendingReference(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression)) return false;
    PsiElement target = ((PsiReferenceExpression)expression).resolve();
    if (!(target instanceof PsiLocalVariable)) return false;
    PsiLocalVariable local = (PsiLocalVariable)target;
    if (!local.getTypeElement().isInferredType()) return false;
    PsiExpression initializer = local.getInitializer();
    if (initializer == null) return false;
    PsiType initializerType = initializer.getType();
    return initializerType == null && mayHaveUnknownTypeDueToPendingReference(initializer) ||
           PsiTypes.nullType().equals(initializerType) && mayHaveNullTypeDueToPendingReference(initializer);
  }

  /**
   * @param ref unresolved reference to find potential imports for
   * @return list of import statements that potentially import the given unresolved reference
   */
  public static List<PsiImportStatementBase> getPotentialImports(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiImportStatementBase || ref.isQualified()) return Collections.emptyList();
    boolean maybeClass = canBeClassReference(ref);
    if (!(ref.getContainingFile() instanceof PsiJavaFile)) return Collections.emptyList();
    PsiImportList list = ((PsiJavaFile)ref.getContainingFile()).getImportList();
    List<PsiImportStatementBase> imports = new ArrayList<>();
    if (list != null) {
      for (PsiImportStatementBase statement : list.getAllImportStatements()) {
        if (statement instanceof PsiImportStaticStatement && ((PsiImportStaticStatement)statement).resolveTargetClass() != null) continue;
        if (!statement.isOnDemand()) {
          PsiJavaCodeReferenceElement reference = statement.getImportReference();
          if (reference == null) continue;
          String name = reference.getReferenceName();
          if (name == null || !name.equals(ref.getReferenceName())) continue;
          if (reference.resolve() != null) continue;
        }
        // Unqualified method call cannot be imported using non-static import
        if (maybeClass || statement instanceof PsiImportStaticStatement) {
          imports.add(statement);
        }
      }
    }
    return imports;
  }

  /**
   * @param ref reference to check
   * @return true if this reference could be a reference to a class
   */
  public static boolean canBeClassReference(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiMethodCallExpression) return false;
    if (!(ref instanceof PsiReferenceExpression)) return true;
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifierExpression() == ref) return true;
    return false;
  }

  /**
   * @param ref reference to check
   * @return true if the reference can be pending. A pending reference is an unresolved reference that can be potentially resolved
   * once the project dependencies are properly resolved. Not every reference can be pending. E.g., an unresolved method inside
   * the fully resolved class cannot be pending.
   */
  public static boolean canBePendingReference(@NotNull PsiJavaCodeReferenceElement ref) {
    if (ref instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)ref).getQualifierExpression();
      if (qualifier == null) {
        PsiClass psiClass = PsiUtil.getContainingClass(ref);
        while (psiClass != null) {
          if (!isHierarchyResolved(psiClass)) return true;
          psiClass = PsiUtil.getContainingClass(psiClass);
        }
        return !getPotentialImports(ref).isEmpty();
      }
      if (qualifier instanceof PsiReferenceExpression) {
        PsiElement qualifierTarget = ((PsiReferenceExpression)qualifier).resolve();
        if (qualifierTarget == null && canBePendingReference((PsiReferenceExpression)qualifier)) {
          return true;
        }
        if (qualifierTarget instanceof PsiClass && isHierarchyResolved((PsiClass)qualifierTarget)) {
          return false;
        }
      }
      PsiType qualifierType = qualifier.getType();
      if (isUnresolvedClassType(qualifierType) ||
          (qualifierType == null && mayHaveUnknownTypeDueToPendingReference(qualifier)) ||
          (PsiTypes.nullType().equals(qualifierType) && mayHaveNullTypeDueToPendingReference(qualifier))) {
        return true;
      }
    }
    else {
      return true;
    }
    return false;
  }
}
