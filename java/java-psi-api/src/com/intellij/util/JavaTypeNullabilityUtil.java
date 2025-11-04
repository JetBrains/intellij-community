// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.codeInsight.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Helper methods to compute nullability of Java types.
 */
@ApiStatus.Internal
public final class JavaTypeNullabilityUtil {
  /**
   * Computes the class type nullability
   * 
   * @param type type to compute nullability for
   * @return type nullability
   */
  public static @NotNull TypeNullability getTypeNullability(@NotNull PsiClassType type) {
    return getTypeNullability(type, null, !isLocal(type));
  }
  
  private static @NotNull TypeNullability getTypeNullability(@NotNull PsiClassType type,
                                                             @Nullable Set<PsiClassType> visited,
                                                             boolean checkContainer) {
    if (visited != null && visited.contains(type)) return TypeNullability.UNKNOWN;
    TypeNullability fromAnnotations = getNullabilityFromAnnotations(type.getAnnotations());
    if (!fromAnnotations.equals(TypeNullability.UNKNOWN)) return fromAnnotations;
    PsiElement context = type.getPsiContext();
    if (context != null && checkContainer) {
      NullableNotNullManager manager = NullableNotNullManager.getInstance(context.getProject());
      if (manager != null) {
        NullabilityAnnotationInfo typeUseNullability = manager.findDefaultTypeUseNullability(context);
        if (typeUseNullability != null) {
          return typeUseNullability.toTypeNullability();
        }
      }
    }
    PsiClass target = type.resolve();
    //inferred types don't have contexts
    if (target instanceof PsiTypeParameter && context != null) {
      PsiTypeParameter typeParameter = (PsiTypeParameter)target;
      PsiReferenceList extendsList = typeParameter.getExtendsList();
      PsiClassType[] extendTypes = extendsList.getReferencedTypes();
      if (extendTypes.length == 0 && checkContainer) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(typeParameter.getProject());
        // If there's no bound, we assume an implicit `extends Object` bound, which is subject to default annotation if any.
        NullabilityAnnotationInfo typeUseNullability = manager == null ? null : manager.findDefaultTypeUseNullability(typeParameter);
        if (typeUseNullability != null) {
          return typeUseNullability.toTypeNullability().inherited();
        }
      } else {
        Set<PsiClassType> nextVisited = visited == null ? new HashSet<>() : visited;
        nextVisited.add(type);
        List<TypeNullability> nullabilities = ContainerUtil.map(extendTypes, t -> getTypeNullability(t, nextVisited, checkContainer));
        return TypeNullability.intersect(nullabilities).inherited();
      }
    }
    return TypeNullability.UNKNOWN;
  }
  
  private static boolean isLocal(PsiClassType classType) {
    PsiElement context = classType.getPsiContext();
    return context instanceof PsiJavaCodeReferenceElement &&
           context.getParent() instanceof PsiTypeElement &&
           context.getParent().getParent() instanceof PsiLocalVariable;
  }

  /**
   * Computes the nullability from explicit annotations
   * @param annotations array of annotations to look for nullability annotations in
   * @return nullability from explicit annotations
   */
  public static @NotNull TypeNullability getNullabilityFromAnnotations(@NotNull PsiAnnotation @NotNull [] annotations) {
    for (PsiAnnotation annotation : annotations) {
      String qualifiedName = annotation.getQualifiedName();
      NullableNotNullManager manager = NullableNotNullManager.getInstance(annotation.getProject());
      if (manager == null) return TypeNullability.UNKNOWN;
      Optional<Nullability> optionalNullability = manager.getAnnotationNullability(qualifiedName);
      if (optionalNullability.isPresent()) {
        Nullability nullability = optionalNullability.get();
        return new TypeNullability(nullability, new NullabilitySource.ExplicitAnnotation(annotation));
      }
    }
    return TypeNullability.UNKNOWN;
  }

  /**
   * Checks whether {@code rightType} can be assigned into {@code leftType} from the point of nullability in the type parameters.
   * @param leftType type to assign to
   * @param rightType assigned value
   * @param checkNotNullToNull whether to check for nullability conflict when assigning not-null to null value
   *
   * @see JavaTypeNullabilityUtil#getNullabilityConflictType(PsiType, PsiType)
   */
  public static @NotNull NullabilityConflict getNullabilityConflictInAssignment(@Nullable PsiType leftType,
                                                                                @Nullable PsiType rightType,
                                                                                boolean checkNotNullToNull) {
    return getNullabilityConflictInAssignment(leftType, rightType, checkNotNullToNull, false);
  }

  private static @NotNull NullabilityConflict getNullabilityConflictInAssignment(@Nullable PsiType leftType,
                                                                                 @Nullable PsiType rightType,
                                                                                 boolean checkNotNullToNull,
                                                                                 boolean checkConflictInInitialType) {
    if (checkConflictInInitialType) {
      NullabilityConflict nullabilityConflict = getNullabilityConflictType(leftType, rightType);
      if (isAllowedNullabilityConflictType(checkNotNullToNull, nullabilityConflict)) return nullabilityConflict;
    }

    if (leftType == null || TypeConversionUtil.isNullType(leftType) ||
        rightType == null || TypeConversionUtil.isNullType(rightType)
    ) {
      return NullabilityConflict.UNKNOWN;
    }

    if (rightType instanceof PsiIntersectionType) {
      return getNullabilityConflictInTypeArguments(leftType, rightType, checkNotNullToNull);
    }

    if (rightType instanceof PsiCapturedWildcardType) {
      return getNullabilityConflictInAssignment(leftType, ((PsiCapturedWildcardType)rightType).getUpperBound(true), checkNotNullToNull,
                                                false);
    }
    if (leftType instanceof PsiCapturedWildcardType) {
      return getNullabilityConflictInAssignment(((PsiCapturedWildcardType)leftType).getLowerBound(), rightType, checkNotNullToNull, false);
    }

    if (leftType instanceof PsiWildcardType) {
      return getNullabilityConflictInAssignment(GenericsUtil.getWildcardBound(leftType), rightType, checkNotNullToNull, false);
    }
    if (rightType instanceof PsiWildcardType) {
      return getNullabilityConflictInAssignment(leftType, GenericsUtil.getWildcardBound(rightType), checkNotNullToNull, false);
    }

    if (leftType instanceof PsiArrayType && rightType instanceof PsiArrayType) {
      return getNullabilityConflictInAssignment(((PsiArrayType)leftType).getComponentType(),
                                                ((PsiArrayType)rightType).getComponentType(), checkNotNullToNull, true);
    }

    if (!(leftType instanceof PsiClassType) || !(rightType instanceof PsiClassType)) {
      return NullabilityConflict.UNKNOWN;
    }

    return getNullabilityConflictInTypeArguments(leftType, rightType, checkNotNullToNull);
  }

  /**
   * Finds first inconsistencies in nullability inside generic class type arguments.
   * For example, {@code GenericClass<@NotNull A, @Nullable B, @NotNull C> = GenericClass<@Nullable A, @Nullable B, @NotNull C>} will have
   * conflicts in the first and third type arguments.
   * <p>
   * Note: this method also treats intersection type as type with arguments.
   * @param checkNotNullToNull whether to check for nullability conflict when assigning not-null to null value.
   * @return first inconsistency in nullability inside generic class type arguments.
   */
  private static @NotNull NullabilityConflict getNullabilityConflictInTypeArguments(@NotNull PsiType leftType,
                                                                                    @NotNull PsiType rightType,
                                                                                    boolean checkNotNullToNull) {
    if (isRawType(leftType) || isRawType(rightType)) return NullabilityConflict.UNKNOWN;
    PsiClass leftClass = PsiTypesUtil.getPsiClass(leftType);
    if (leftClass == null) return NullabilityConflict.UNKNOWN;

    List<PsiType> leftParameterTypeList = getParentParameterTypeListFromDerivedType(leftType, leftClass);
    List<PsiType> rightParameterTypeList = getParentParameterTypeListFromDerivedType(rightType, leftClass);
    if (leftParameterTypeList == null ||
        rightParameterTypeList == null ||
        leftParameterTypeList.size() != rightParameterTypeList.size()) {
      return NullabilityConflict.UNKNOWN;
    }

    for (int i = 0; i < leftParameterTypeList.size(); i++) {
      PsiType leftParameterType = leftParameterTypeList.get(i);
      PsiType rightParameterType = rightParameterTypeList.get(i);

      NullabilityConflict nullabilityConflict = getNullabilityConflictInAssignment(
        leftParameterType,
        rightParameterType,
        checkNotNullToNull,
        true
      );
      if (nullabilityConflict != NullabilityConflict.UNKNOWN) return nullabilityConflict;
    }

    return NullabilityConflict.UNKNOWN;
  }

  private static boolean isRawType(@NotNull PsiType type) {
    return type instanceof PsiClassType && ((PsiClassType)type).isRaw();
  }

  private static @Nullable List<@NotNull PsiType> getParentParameterTypeListFromDerivedType(@NotNull PsiType derivedType,
                                                                                            @NotNull PsiClass superClass) {
    if (derivedType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)derivedType).getConjuncts()) {
        List<@NotNull PsiType> candidates = getParentParameterTypeListFromDerivedType(conjunct, superClass);
        if (candidates != null) return candidates;
      }
      return null;
    }
    return PsiUtil.substituteTypeParameters(derivedType, superClass, true);
  }

  private static boolean isAllowedNullabilityConflictType(boolean checkNotNullToNull, @NotNull NullabilityConflict nullabilityConflict) {
    return nullabilityConflict != NullabilityConflict.UNKNOWN &&
           (checkNotNullToNull || nullabilityConflict != NullabilityConflict.NOT_NULL_TO_NULL);
  }

  /**
   * Checks whether {@code rightType} can be assigned into {@code leftType} from the point of nullability.
   * @param leftType type to assign to
   * @param rightType assigned value
   * @see NullabilityConflict
   */
  public static @NotNull NullabilityConflict getNullabilityConflictType(@Nullable PsiType leftType, @Nullable PsiType rightType) {
    if (leftType == null || rightType == null) return NullabilityConflict.UNKNOWN;
    TypeNullability leftTypeNullability = leftType.getNullability();
    TypeNullability rightTypeNullability = rightType.getNullability();
    Nullability leftNullability = leftTypeNullability.nullability();
    Nullability rightNullability = rightTypeNullability.nullability();

    if (leftNullability == Nullability.NOT_NULL && rightNullability == Nullability.NULLABLE) return NullabilityConflict.NULL_TO_NOT_NULL;
    // It is not possible to have NOT_NULL_TO_NULL conflict when left type is wildcard with upper bound,
    // e.g., this assignment is legal {@code List<? extends @Nullable Object> = List<@NotNull String>}
    else if (leftNullability == Nullability.NULLABLE && rightNullability == Nullability.NOT_NULL && !GenericsUtil.isWildcardWithExtendsBound(leftType)) return NullabilityConflict.NOT_NULL_TO_NULL;
    return NullabilityConflict.UNKNOWN;
  }

  /**
   * Represents a conflict in nullability between 2 types
   */
  public enum NullabilityConflict {
    /**
     * Attempt to assign null to a not-null type, example: {@code Container<@NotNull T> c = Container<@Nullable T>}
     */
    NULL_TO_NOT_NULL,
    /**
     * Attempt to assign not-null to a null type, example: {@code Container<@Nullable> c <- Container<@NotNull T>}
    */
    NOT_NULL_TO_NULL,
    /**
     * There is no conflict or it is unknown
     */
    UNKNOWN
  }
}
