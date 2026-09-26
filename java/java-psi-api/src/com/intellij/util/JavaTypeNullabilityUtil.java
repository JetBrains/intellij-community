// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullabilitySource;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
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
   * Limits the walk of {@link #withNullabilityFrom}, so that a recursive generic type cannot make it loop.
   */
  private static final int MAX_NULLABILITY_OVERLAY_DEPTH = 10;

  /**
   * The nullability of the implicit upper bound of an unbounded wildcard {@code ?} inside a {@code @NullMarked} scope.
   * Per the JSpecify spec ("bound of an unbounded wildcard"), it is base type {@code Object} with nullness operator
   * {@code UNION_NULL} (i.e. nullable). It is modeled as inherited from a bound to mirror {@code ? extends @Nullable Object}.
   */
  private static final TypeNullability UNBOUNDED_WILDCARD_NULLABILITY =
    new TypeNullability(Nullability.NULLABLE, NullabilitySource.Standard.KNOWN).inherited();

  /**
   * Computes the class type nullability
   *
   * @param type type to compute nullability for
   * @return type nullability
   */
  public static @NotNull TypeNullability getTypeNullability(@NotNull PsiClassType type) {
    return getTypeNullability(type, null, !shouldIgnoreContainer(type));
  }

  private static @NotNull TypeNullability getTypeNullability(@NotNull PsiClassType type,
                                                             @Nullable Set<PsiClassType> visited,
                                                             boolean checkContainer) {
    PsiElement context = type.getPsiContext();
    if (isWrittenInPatternType(context)) return TypeNullability.UNKNOWN;
    if (visited != null && visited.contains(type)) return TypeNullability.UNKNOWN;
    TypeNullability fromAnnotations = getNullabilityFromAnnotations(type.getAnnotations());
    boolean explicit = !fromAnnotations.equals(TypeNullability.UNKNOWN);
    if (explicit) return fromAnnotations;
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
      List<TypeNullability> nullabilities =
        ContainerUtil.map(typeParameter.getSuperTypes(), t -> getTypeNullability(t, nextVisited, checkContainer));
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

  /**
   * Checks whether the type was written inside a pattern. The JSpecify spec calls "any component in a pattern" an
   * unrecognized type-use location, because nullness is not reified and an {@code instanceof} cannot check it. A nullness
   * written there is ignored, at the top level and inside every type argument, and a container annotation such as
   * {@code @NullMarked} does not apply either. The nullness of a pattern variable comes from the context type instead;
   * see {@code JavaPsiPatternUtil#getEffectivePatternType}.
   * <p>
   * The walk goes only through the element kinds that can separate a pattern type reference from its pattern, so it
   * stops after a few steps for a type written anywhere else.
   *
   * @param context PSI context of the type, usually the reference element that the type was created from
   * @return true if the type was written in a pattern type
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean isWrittenInPatternType(@Nullable PsiElement context) {
    for (PsiElement element = context; element != null; element = element.getParent()) {
      if (element instanceof PsiPattern) return true;
      if (!(element instanceof PsiJavaCodeReferenceElement ||
            element instanceof PsiTypeElement ||
            element instanceof PsiReferenceParameterList ||
            element instanceof PsiAnnotation ||
            element instanceof PsiPatternVariable)) {
        return false;
      }
    }
    return false;
  }

  /**
   * Copies the nullability of one type onto a structurally equal type. The walk goes into the type arguments, the array
   * components and the wildcard bounds. A part where the two types are not structurally equal keeps its own nullability.
   *
   * @param target type to copy the nullability to
   * @param source type to copy the nullability from
   * @return the target type with the nullability of the source type
   */
  public static @NotNull PsiType withNullabilityFrom(@NotNull PsiType target, @NotNull PsiType source) {
    return withNullabilityFrom(target, source, 0);
  }

  private static @NotNull PsiType withNullabilityFrom(@NotNull PsiType target, @NotNull PsiType source, int depth) {
    if (depth > MAX_NULLABILITY_OVERLAY_DEPTH) return target;
    if (target instanceof PsiArrayType && !(target instanceof PsiEllipsisType) && source instanceof PsiArrayType) {
      PsiType component = withNullabilityFrom(((PsiArrayType)target).getComponentType(),
                                              ((PsiArrayType)source).getComponentType(), depth + 1);
      return new PsiArrayType(component, target.getAnnotationProvider()).withNullability(source.getNullability());
    }
    if (target instanceof PsiWildcardType && source instanceof PsiWildcardType) {
      PsiWildcardType targetWildcard = (PsiWildcardType)target;
      PsiWildcardType sourceWildcard = (PsiWildcardType)source;
      PsiType targetBound = targetWildcard.getBound();
      PsiType sourceBound = sourceWildcard.getBound();
      if (targetBound == null || sourceBound == null || targetWildcard.isExtends() != sourceWildcard.isExtends()) {
        return target.withNullability(source.getNullability());
      }
      PsiType bound = withNullabilityFrom(targetBound, sourceBound, depth + 1);
      return targetWildcard.isExtends()
             ? PsiWildcardType.createExtends(targetWildcard.getManager(), bound)
             : PsiWildcardType.createSuper(targetWildcard.getManager(), bound);
    }
    if (!(target instanceof PsiClassType) || !(source instanceof PsiClassType)) {
      return target.withNullability(source.getNullability());
    }
    PsiClassType.ClassResolveResult targetResult = ((PsiClassType)target).resolveGenerics();
    PsiClassType.ClassResolveResult sourceResult = ((PsiClassType)source).resolveGenerics();
    PsiClass targetClass = targetResult.getElement();
    if (targetClass == null || targetClass != sourceResult.getElement() || ((PsiClassType)target).isRaw()) {
      return target.withNullability(source.getNullability());
    }
    PsiSubstitutor targetSubstitutor = targetResult.getSubstitutor();
    PsiSubstitutor sourceSubstitutor = sourceResult.getSubstitutor();
    PsiSubstitutor result = PsiSubstitutor.EMPTY;
    boolean changed = false;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(targetClass)) {
      PsiType targetArgument = targetSubstitutor.substitute(parameter);
      PsiType sourceArgument = sourceSubstitutor.substitute(parameter);
      PsiType argument = targetArgument == null || sourceArgument == null
                         ? targetArgument
                         : withNullabilityFrom(targetArgument, sourceArgument, depth + 1);
      changed |= argument != targetArgument;
      result = result.put(parameter, argument);
    }
    if (!changed) return target.withNullability(source.getNullability());
    return JavaPsiFacade.getElementFactory(targetClass.getProject()).createType(targetClass, result)
      .withNullability(source.getNullability());
  }

  private static boolean shouldIgnoreContainer(PsiClassType classType) {
    PsiElement context = classType.getPsiContext();
    return context instanceof PsiJavaCodeReferenceElement &&
           context.getParent() instanceof PsiTypeElement &&
           shouldIgnoreContainer(context.getParent().getParent());
  }

  /**
   * @param element type element owner to check
   * @return true if for this type owner, the container annotation like {@code @NullMarked} should be ignored
   * (for example, if the element is a local variable)
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean shouldIgnoreContainer(@Nullable PsiElement element) {
    return element instanceof PsiLocalVariable ||
           element instanceof PsiParameter && !(element.getParent() instanceof PsiParameterList);
  }

  /**
   * Computes the nullability from explicit annotations
   *
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
   *
   * @param leftType         type to assign to
   * @param declaredLeftType the left type before substitution of the enclosing method/class type arguments
   * @param rightType        assigned value
   * @param options          how to check nullability conflicts
   * @see #getNullabilityConflictTypeContext(PsiType, PsiType)
   */
  public static @NotNull NullabilityConflictContext getNullabilityConflictInAssignment(@Nullable PsiType leftType,
                                                                                       @Nullable PsiType declaredLeftType,
                                                                                       @Nullable PsiType rightType,
                                                                                       @NotNull NullabilityConflictOptions options) {
    return getNullabilityConflictInAssignment(leftType, declaredLeftType, rightType, 0, options);
  }

  private static @NotNull NullabilityConflictContext getNullabilityConflictInAssignment(@Nullable PsiType leftType,
                                                                                        @Nullable PsiType declaredLeftType,
                                                                                        @Nullable PsiType rightType,
                                                                                        int level,
                                                                                        @NotNull NullabilityConflictOptions options) {
    if (leftType == null || TypeConversionUtil.isNullType(leftType) ||
        rightType == null || TypeConversionUtil.isNullType(rightType)
    ) {
      return NullabilityConflictContext.UNKNOWN;
    }

    if (rightType instanceof PsiIntersectionType) {
      return getNullabilityConflictInTypeArguments(leftType, declaredLeftType, rightType, level, options);
    }

    if (rightType instanceof PsiCapturedWildcardType) {
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(leftType, rightType);
        if (isAllowedNullabilityConflictType(level > 1 && options.checkNotNullToNull, context)) return context;
      }
      return getNullabilityConflictInAssignment(leftType, declaredLeftType, ((PsiCapturedWildcardType)rightType).getUpperBound(true), level,
                                                options);
    }
    if (leftType instanceof PsiCapturedWildcardType) {
      PsiWildcardType leftWildcard = ((PsiCapturedWildcardType)leftType).getWildcard();
      return getNullabilityConflictForLeftWildCard(leftWildcard, rightType, level, options);
    }
    if (rightType instanceof PsiWildcardType) {
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(leftType, rightType);
        if (isAllowedNullabilityConflictType(level > 1 && options.checkNotNullToNull, context)) return context;
      }
      return getNullabilityConflictInAssignment(leftType, declaredLeftType, GenericsUtil.getWildcardBound(rightType), level, options);
    }
    if (leftType instanceof PsiWildcardType) {
      PsiWildcardType leftWildcard = (PsiWildcardType)leftType;
      return getNullabilityConflictForLeftWildCard(leftWildcard, rightType, level, options);
    }
    if (leftType instanceof PsiArrayType && rightType instanceof PsiArrayType) {
      PsiType leftComponent = ((PsiArrayType)leftType).getComponentType();
      PsiType rightComponent = ((PsiArrayType)rightType).getComponentType();
      NullabilityConflictContext context = getNullabilityConflictTypeContext(leftComponent, rightComponent);
      if (isAllowedNullabilityConflictType(level != 0 && options.checkNotNullToNull, context)) return context;
      return getNullabilityConflictInAssignment(leftComponent,
                                                null, rightComponent, level, options);
    }

    if (!(leftType instanceof PsiClassType) || !(rightType instanceof PsiClassType)) {
      return NullabilityConflictContext.UNKNOWN;
    }

    return getNullabilityConflictInTypeArguments(leftType, declaredLeftType, rightType, level, options);
  }

  private static @NotNull NullabilityConflictContext getNullabilityConflictForLeftWildCard(@Nullable PsiWildcardType leftWildcard,
                                                                                           @Nullable PsiType rightType,
                                                                                           int level,
                                                                                           @NotNull NullabilityConflictOptions options) {
    if (leftWildcard == null || rightType == null) return NullabilityConflictContext.UNKNOWN;
    PsiType leftBound = GenericsUtil.getWildcardBound(leftWildcard);
    if (leftWildcard.isSuper()) {
      if (rightType instanceof PsiWildcardType && ((PsiWildcardType)rightType).isSuper()) {
        rightType = GenericsUtil.getWildcardBound(rightType);
      }
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(rightType, leftBound);
        if (isAllowedNullabilityConflictType(level > 1 && options.checkNotNullToNull, context)) {
          context = new NullabilityConflictContext(NullabilityConflict.COMPLEX, leftBound, rightType);
          return context;
        }
      }
      NullabilityConflictContext context = getNullabilityConflictInAssignment(rightType, null, leftBound, level, options);
      if (context.nullabilityConflict != NullabilityConflict.UNKNOWN) {
        context = new NullabilityConflictContext(NullabilityConflict.COMPLEX, leftBound, rightType);
      }
      return context;
    }
    else {
      if (level > 0) {
        NullabilityConflictContext context = getNullabilityConflictTypeContext(leftBound, rightType);
        if (isAllowedNullabilityConflictType(level > 1 && options.checkNotNullToNull, context)) return context;
      }
      return getNullabilityConflictInAssignment(leftBound, null, rightType, level, options);
    }
  }

  /**
   * Finds first inconsistencies in nullability inside generic class type arguments.
   * For example, {@code GenericClass<@NotNull A, @Nullable B, @NotNull C> = GenericClass<@Nullable A, @Nullable B, @NotNull C>} will have
   * conflicts in the first and third type arguments.
   * <p>
   * Note: this method also treats intersection type as type with arguments.
   *
   * @return first inconsistency in nullability inside generic class type arguments.
   */
  private static @NotNull NullabilityConflictContext getNullabilityConflictInTypeArguments(@NotNull PsiType leftType,
                                                                                           @Nullable PsiType declaredLeftType,
                                                                                           @NotNull PsiType rightType,
                                                                                           int level,
                                                                                           @NotNull NullabilityConflictOptions options) {
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
    // The declared (pre-substitution) left type arguments, used only to recover the declared bound of a type
    // variable that substitution replaced. Null if unavailable or structurally incompatible.
    List<PsiType> declaredLeftParameterTypeList =
      declaredLeftType == null ? null : getParentParameterTypeListFromDerivedType(declaredLeftType, leftClass);
    if (declaredLeftParameterTypeList != null && declaredLeftParameterTypeList.size() != leftParameterTypeList.size()) {
      declaredLeftParameterTypeList = null;
    }
    boolean rightInNullMarkedScope = isInNullMarkedScope(rightType);
    PsiTypeParameter[] typeParameters = leftClass.getTypeParameters();

    for (int i = 0; i < leftParameterTypeList.size(); i++) {
      PsiType leftParameterType = leftParameterTypeList.get(i);
      PsiType rightParameterType = rightParameterTypeList.get(i);
      PsiType declaredLeftParameterType = declaredLeftParameterTypeList == null ? null : declaredLeftParameterTypeList.get(i);
      // Example:
      // new Box<Container<? extends Foo>, Container<? extends @Nullable Foo>>();
      //
      // @NullMarked interface Container<T extends Foo>
      // Foo is already bounded as not-null and ? extends @Nullable doesn't change it.
      if (i < typeParameters.length &&
          leftParameterType instanceof PsiWildcardType && ((PsiWildcardType)leftParameterType).isExtends() &&
          rightParameterType instanceof PsiWildcardType && ((PsiWildcardType)rightParameterType).isExtends() &&
          TypeNullability.ofTypeParameter(typeParameters[i]).nullability() == Nullability.NOT_NULL) {
        PsiType leftBound = GenericsUtil.getWildcardBound(leftParameterType);
        PsiType rightBound = GenericsUtil.getWildcardBound(rightParameterType);
        if (leftBound != null && rightBound != null) {
          PsiType declaredLeftBound = declaredLeftParameterType == null ? null : GenericsUtil.getWildcardBound(declaredLeftParameterType);
          NullabilityConflictContext nested =
            getNullabilityConflictInAssignment(leftBound, declaredLeftBound, rightBound, level + 1, options);
          if (nested.nullabilityConflict != NullabilityConflict.UNKNOWN) return nested;
        }
        continue;
      }

      // An unbounded '?' has an implicit '@Nullable Object' upper bound in a @NullMarked scope (JSpecify spec,
      // "Bound of an 'unbounded' wildcard").
      //
      // A '? super X' is not covered by that section; the spec handles it via
      // capture conversion in nullness-subtyping.
      // We approximate that captured upper bound as nullable so that
      // '? super' is not contained in a non-null '? extends' bound.
      if (rightInNullMarkedScope &&
          rightParameterType instanceof PsiWildcardType &&
          rightParameterType.getNullability().nullability() == Nullability.UNKNOWN &&
          (!((PsiWildcardType)rightParameterType).isBounded() ||
           (((PsiWildcardType)rightParameterType).isSuper() &&
            leftParameterType instanceof PsiWildcardType && ((PsiWildcardType)leftParameterType).isExtends()))) {
        PsiWildcardType rightWildcard = (PsiWildcardType)rightParameterType;
        rightParameterType = rightWildcard.withNullability(UNBOUNDED_WILDCARD_NULLABILITY);
      }

      NullabilityConflictContext contextTheCurrentCheck =
        getNullabilityConflictTypeContext(leftParameterType, rightParameterType);
      if (isAllowedNullabilityConflictType(options.checkNotNullToNull, contextTheCurrentCheck)) return contextTheCurrentCheck;

      NullabilityConflictContext context = getNullabilityConflictInAssignment(
        leftParameterType,
        declaredLeftParameterType, rightParameterType,
        level + 1, options
      );
      if (context.nullabilityConflict != NullabilityConflict.UNKNOWN) return context;
    }

    return NullabilityConflictContext.UNKNOWN;
  }

  private static boolean isRawType(@NotNull PsiType type) {
    return type instanceof PsiClassType && ((PsiClassType)type).isRaw();
  }

  private static boolean isInNullMarkedScope(@NotNull PsiType type) {
    PsiElement context = type instanceof PsiClassType ? ((PsiClassType)type).getPsiContext() : null;
    if (context == null) return false;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(context.getProject());
    if (manager == null) return false;
    NullabilityAnnotationInfo info = manager.findDefaultTypeUseNullability(context);
    return info != null && info.getNullability() == Nullability.NOT_NULL && info.isContainer();
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
   *
   * @param leftType  type to assign to
   * @param rightType assigned value
   * @see NullabilityConflict
   */
  private static @NotNull NullabilityConflictContext getNullabilityConflictTypeContext(@Nullable PsiType leftType,
                                                                                       @Nullable PsiType rightType) {
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
    else if (leftNullability == Nullability.NULLABLE &&
             rightNullability == Nullability.NOT_NULL &&
             !GenericsUtil.isWildcardWithExtendsBound(leftType)) {
      return new NullabilityConflictContext(NullabilityConflict.NOT_NULL_TO_NULL, leftType, rightType);
    }
    // its actual nullability depends on the instantiation (e.g. `T` might be instantiated as `@NotNull`),
    else if (leftNullability == Nullability.NULLABLE && rightNullability == Nullability.NULLABLE &&
             isInheritedFromBound(leftTypeNullability) != isInheritedFromBound(rightTypeNullability) &&
             //`List<? extends @Nullable Object> can take everything`
             !GenericsUtil.isWildcardWithExtendsBound(leftType)) {
      return new NullabilityConflictContext(NullabilityConflict.COMPLEX, leftType, rightType);
    }

    return NullabilityConflictContext.UNKNOWN;
  }

  private static boolean isInheritedFromBound(@NotNull TypeNullability nullability) {
    return nullability.source() instanceof NullabilitySource.ExtendsBound;
  }

  /**
   * Holds information about options for the nullability conflict analysis.
   */
  public static class NullabilityConflictOptions {
    /**
     * Report assignment of a not-null type argument to a nullable type argument
     */
    public final boolean checkNotNullToNull;

    public NullabilityConflictOptions(boolean checkNotNullToNull) {
      this.checkNotNullToNull = checkNotNullToNull;
    }
  }

  /**
   * Holds information about the nullability conflict that might be used to provide more descriptive error messages.
   */
  public static class NullabilityConflictContext {
    private final @NotNull NullabilityConflict nullabilityConflict;
    private final @Nullable PsiType expectedType;
    private final @Nullable PsiType actualType;
    public static final NullabilityConflictContext UNKNOWN = new NullabilityConflictContext(NullabilityConflict.UNKNOWN, null, null);

    public NullabilityConflictContext(@NotNull NullabilityConflict nullabilityConflict,
                                      @Nullable PsiType expectedType,
                                      @Nullable PsiType actualType) {
      this.nullabilityConflict = nullabilityConflict;
      this.expectedType = expectedType;
      this.actualType = actualType;
    }

    /**
     * @return nullability conflict type
     * @see NullabilityConflict
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
        return getPlace(((PsiCapturedWildcardType)placeHolder).getWildcard());
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
   *
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