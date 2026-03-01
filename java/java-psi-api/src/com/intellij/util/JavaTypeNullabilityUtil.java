// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullabilitySource;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
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
      Set<PsiClassType> nextVisited = visited == null ? new HashSet<>() : visited;
      nextVisited.add(type);
      //superTypes returns Object for `empty` parameter list, which can contain external annotations
      List<TypeNullability> nullabilities = ContainerUtil.map(typeParameter.getSuperTypes(), t -> getTypeNullability(t, nextVisited, checkContainer));
      TypeNullability fromSuper = TypeNullability.intersect(nullabilities).inherited();
      if (fromSuper != TypeNullability.UNKNOWN) return fromSuper;
      //but this Object cannot hold nullability container, because doesn't have a reference
      if (extendTypes.length == 0 && checkContainer) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(typeParameter.getProject());
        if (manager != null) {
          NullabilityAnnotationInfo effective = manager.findOwnNullabilityInfo(typeParameter);
          if (effective != null) {
            return effective.toTypeNullability().inherited();
          }
          // If there's no bound, we assume an implicit `extends Object` bound, which is subject to default annotation if any.
          NullabilityAnnotationInfo typeUseNullability = manager.findDefaultTypeUseNullability(typeParameter);
          if (typeUseNullability != null) {
            return typeUseNullability.toTypeNullability().inherited();
          }
        }
      }
    }
    return TypeNullability.UNKNOWN;
  }

  private static boolean isLocal(PsiClassType classType) {
    PsiElement context = classType.getPsiContext();
    return context instanceof PsiJavaCodeReferenceElement &&
           context.getParent() instanceof PsiTypeElement &&
           isLocalVariable(context.getParent().getParent());
  }

  private static boolean isLocalVariable(PsiElement element) {
    return element instanceof PsiLocalVariable || 
           element instanceof PsiParameter && !(element.getParent() instanceof PsiParameterList);
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
   * @see JavaTypeNullabilityUtil#getNullabilityConflictTypeContext(PsiType, PsiType)
   */
  public static @NotNull NullabilityConflictContext getNullabilityConflictInAssignment(@Nullable PsiType leftType,
                                                                                @Nullable PsiType rightType,
                                                                                boolean checkNotNullToNull) {
    return getNullabilityConflictInAssignment(leftType, rightType, 0, checkNotNullToNull);
  }

  private static @NotNull NullabilityConflictContext getNullabilityConflictInAssignment(@Nullable PsiType leftType,
                                                                                        @Nullable PsiType rightType,
                                                                                        int level,
                                                                                        boolean checkNotNullToNull) {
    if (leftType == null || TypeConversionUtil.isNullType(leftType) ||
        rightType == null || TypeConversionUtil.isNullType(rightType)
    ) {
      return NullabilityConflictContext.UNKNOWN;
    }

    if (rightType instanceof PsiIntersectionType) {
      return getNullabilityConflictInTypeArguments(leftType, rightType, checkNotNullToNull, level);
    }

    if (rightType instanceof PsiCapturedWildcardType) {
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(leftType, rightType);
        if (isAllowedNullabilityConflictType(level > 1 && checkNotNullToNull, context)) return context;
      }
      return getNullabilityConflictInAssignment(leftType, ((PsiCapturedWildcardType)rightType).getUpperBound(true), level,
                                                checkNotNullToNull
      );
    }
    if (leftType instanceof PsiCapturedWildcardType) {
      PsiWildcardType leftWildcard = ((PsiCapturedWildcardType)leftType).getWildcard();
      return getNullabilityConflictForLeftWildCard(leftWildcard, rightType, level, checkNotNullToNull);
    }
    if (rightType instanceof PsiWildcardType) {
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(leftType, rightType);
        if (isAllowedNullabilityConflictType(level > 1 && checkNotNullToNull, context)) return context;
      }
      return getNullabilityConflictInAssignment(leftType, GenericsUtil.getWildcardBound(rightType), level, checkNotNullToNull);
    }
    if (leftType instanceof PsiWildcardType) {
      PsiWildcardType leftWildcard = (PsiWildcardType)leftType;
      return getNullabilityConflictForLeftWildCard(leftWildcard, rightType, level, checkNotNullToNull);
    }
    if (leftType instanceof PsiArrayType && rightType instanceof PsiArrayType) {
      PsiType leftComponent = ((PsiArrayType)leftType).getComponentType();
      PsiType rightComponent = ((PsiArrayType)rightType).getComponentType();
      NullabilityConflictContext context = getNullabilityConflictTypeContext(leftComponent, rightComponent);
      if (isAllowedNullabilityConflictType(level != 0 && checkNotNullToNull, context)) return context;
      return getNullabilityConflictInAssignment(leftComponent,
                                                rightComponent, level, checkNotNullToNull);
    }

    if (!(leftType instanceof PsiClassType) || !(rightType instanceof PsiClassType)) {
      return NullabilityConflictContext.UNKNOWN;
    }

    return getNullabilityConflictInTypeArguments(leftType, rightType, checkNotNullToNull, level);
  }

  private static @NotNull NullabilityConflictContext getNullabilityConflictForLeftWildCard(@Nullable PsiWildcardType leftWildcard,
                                                                                           @Nullable PsiType rightType,
                                                                                           int level,
                                                                                           boolean checkNotNullToNull) {
    if (leftWildcard == null || rightType == null) return NullabilityConflictContext.UNKNOWN;
    PsiType leftBound = GenericsUtil.getWildcardBound(leftWildcard);
    if (leftWildcard.isSuper()) {
      if (rightType instanceof PsiWildcardType && ((PsiWildcardType)rightType).isSuper()) {
        rightType = GenericsUtil.getWildcardBound(rightType);
      }
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(rightType, leftBound);
        if (isAllowedNullabilityConflictType(level > 1 && checkNotNullToNull, context)) {
          context = new NullabilityConflictContext(NullabilityConflict.COMPLEX, leftBound, rightType);
          return context;
        }
      }
      NullabilityConflictContext context = getNullabilityConflictInAssignment(rightType, leftBound, level, checkNotNullToNull);
      if (context.nullabilityConflict != NullabilityConflict.UNKNOWN) {
        context = new NullabilityConflictContext(NullabilityConflict.COMPLEX, leftBound, rightType);
      }
      return context;
    }
    else {
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(leftBound, rightType);
        if (isAllowedNullabilityConflictType(level > 1 && checkNotNullToNull, context)) return context;
      }
      return getNullabilityConflictInAssignment(leftBound, rightType, level, checkNotNullToNull);
    }
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
  private static @NotNull NullabilityConflictContext getNullabilityConflictInTypeArguments(@NotNull PsiType leftType,
                                                                                           @NotNull PsiType rightType,
                                                                                           boolean checkNotNullToNull,
                                                                                           int level) {
    if (isRawType(leftType) || isRawType(rightType)) return NullabilityConflictContext.UNKNOWN;
    PsiClass leftClass = PsiTypesUtil.getPsiClass(leftType);
    if (leftClass == null) return NullabilityConflictContext.UNKNOWN;
    List<PsiType> leftParameterTypeList = getParentParameterTypeListFromDerivedType(leftType, leftClass);
    List<PsiType> rightParameterTypeList = getParentParameterTypeListFromDerivedType(rightType, leftClass);
    if (leftParameterTypeList == null ||
        rightParameterTypeList == null ||
        leftParameterTypeList.size() != rightParameterTypeList.size()) {
      return NullabilityConflictContext.UNKNOWN;
    }

    for (int i = 0; i < leftParameterTypeList.size(); i++) {
      PsiType leftParameterType = leftParameterTypeList.get(i);
      PsiType rightParameterType = rightParameterTypeList.get(i);

      NullabilityConflictContext contextTheCurrentCheck = getNullabilityConflictTypeContext(leftParameterType, rightParameterType);
      if (isAllowedNullabilityConflictType(checkNotNullToNull, contextTheCurrentCheck)) return contextTheCurrentCheck;

      NullabilityConflictContext context = getNullabilityConflictInAssignment(
        leftParameterType,
        rightParameterType,
        level + 1, checkNotNullToNull
      );
      if (context.nullabilityConflict != NullabilityConflict.UNKNOWN) return context;
    }

    return NullabilityConflictContext.UNKNOWN;
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

  private static boolean isAllowedNullabilityConflictType(boolean checkNotNullToNull, @NotNull NullabilityConflictContext context) {
    return context.nullabilityConflict != NullabilityConflict.UNKNOWN &&
           (checkNotNullToNull || context.nullabilityConflict != NullabilityConflict.NOT_NULL_TO_NULL);
  }

  /**
   * Checks whether {@code rightType} can be assigned into {@code leftType} from the point of nullability.
   * @param leftType type to assign to
   * @param rightType assigned value
   * @see NullabilityConflict
   */
  public static @NotNull NullabilityConflictContext getNullabilityConflictTypeContext(@Nullable PsiType leftType, @Nullable PsiType rightType) {
    if (leftType == null || rightType == null) return NullabilityConflictContext.UNKNOWN;
    TypeNullability leftTypeNullability = leftType.getNullability();
    TypeNullability rightTypeNullability = rightType.getNullability();
    Nullability leftNullability = leftTypeNullability.nullability();
    Nullability rightNullability = rightTypeNullability.nullability();

    if (leftNullability == Nullability.NOT_NULL && rightNullability == Nullability.NULLABLE) {
      return new NullabilityConflictContext(NullabilityConflict.NULL_TO_NOT_NULL, leftType, rightType);
    }
    // It is not possible to have NOT_NULL_TO_NULL conflict when left type is wildcard with upper bound,
    // e.g., this assignment is legal {@code List<? extends @Nullable Object> = List<@NotNull String>}
    else if (leftNullability == Nullability.NULLABLE && rightNullability == Nullability.NOT_NULL && !GenericsUtil.isWildcardWithExtendsBound(leftType)) {
      return new NullabilityConflictContext(NullabilityConflict.NOT_NULL_TO_NULL, leftType, rightType);
    }
    return NullabilityConflictContext.UNKNOWN;
  }

  /**
   * Holds information about the nullability conflict that might be used to provide more descriptive error messages.
   */
  public static class NullabilityConflictContext {
    private final @NotNull NullabilityConflict nullabilityConflict;
    private final @Nullable PsiType expectedType;
    private final @Nullable PsiType actualType;
    public static final NullabilityConflictContext UNKNOWN = new NullabilityConflictContext(NullabilityConflict.UNKNOWN, null, null);

    public NullabilityConflictContext(@NotNull NullabilityConflict nullabilityConflict, @Nullable PsiType expectedType, @Nullable PsiType actualType) {
      this.nullabilityConflict = nullabilityConflict;
      this.expectedType = expectedType;
      this.actualType = actualType;
    }

    /**
     * @see NullabilityConflict
     * @return nullability conflict type
     */
    public @NotNull NullabilityConflict nullabilityConflict() {
      return nullabilityConflict;
    }

    /**
     * @return part of the actual {@code PsiType} in which the conflict is occurred.
     */
    public PsiType getType(@NotNull Side side) {
      if (side == Side.EXPECTED) return expectedType;
      return actualType;
    }

    /**
     * @return type argument or array type in which the conflict is occurred.
     */
    public @Nullable PsiElement getPlace(@NotNull Side side) {
      PsiType type = getType(side);
      return getPlace(type);
    }


    /**
     * @return nullability annotation that produces the conflict.
     */
    public @Nullable PsiAnnotation getAnnotation(@NotNull Side side) {
      PsiType type = getType(side);
      if (type == null) return null;
      TypeNullability nullability = type.getNullability();
      NullabilityAnnotationInfo info = nullability.toNullabilityAnnotationInfo();
      if (info == null) return null;
      return info.getAnnotation();
    }

    private static @Nullable PsiElement getPlace(PsiType placeHolder) {
      if (placeHolder instanceof PsiClassType) {
        return ((PsiClassType)placeHolder).getPsiContext();
      }
      else if (placeHolder instanceof PsiCapturedWildcardType) {
        return getPlace(((PsiCapturedWildcardType) placeHolder).getWildcard());
      }
      else if (placeHolder instanceof PsiWildcardType) {
        return getPlace(((PsiWildcardType)placeHolder).getBound());
      }
      else if (placeHolder instanceof PsiArrayType) {
        return getPlace(placeHolder.getDeepComponentType());
      }
      return null;
    }
  }

  /**
   * Represents the side of nullability conflict.
   * @see NullabilityConflictContext
   */
  public enum Side {
    EXPECTED,
    ACTUAL,
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
     * Incompatible types, usually, related to {@code ? super Something}
     */
    COMPLEX,
    /**
     * There is no conflict or it is unknown
     */
    UNKNOWN
  }
}