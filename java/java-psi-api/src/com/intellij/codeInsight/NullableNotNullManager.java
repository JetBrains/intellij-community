// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.AnnotationUtil.*;

/**
 * @author anna
 */
public abstract class NullableNotNullManager {
  protected static final Logger LOG = Logger.getInstance(NullableNotNullManager.class);
  protected final Project myProject;

  protected NullableNotNullManager(Project project) {
    myProject = project;
  }

  /**
   * @return list of default non-container annotations that apply to the nullable element
   */
  abstract @NotNull List<String> getDefaultNullables();

  /**
   * @return list of default non-container annotations that apply to the not-null element
   */
  abstract @NotNull List<String> getDefaultNotNulls();

  /**
   * @return list of all default non-container annotations that affect nullability (including nullable, not-null and unknown)
   */
  abstract @NotNull List<String> getAllDefaultAnnotations();

  public static NullableNotNullManager getInstance(Project project) {
    return ServiceManager.getService(project, NullableNotNullManager.class);
  }

  /**
   * @return if owner has a @NotNull or @Nullable annotation, or is in scope of @ParametersAreNullableByDefault or ParametersAreNonnullByDefault
   */
  public boolean hasNullability(@NotNull PsiModifierListOwner owner) {
    return isNullable(owner, false) || isNotNull(owner, false);
  }

  public abstract void setNotNulls(String @NotNull ... annotations);

  public abstract void setNullables(String @NotNull ... annotations);

  public abstract @NotNull String getDefaultNullable();

  /**
   * Returns an annotation which marks given element as Nullable, if any. Usage of this method is discouraged.
   * Use {@link #findEffectiveNullabilityInfo(PsiModifierListOwner)} instead.
   */
  public @Nullable PsiAnnotation getNullableAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullityAnnotationWithDefault(owner, checkBases, true);
  }

  public abstract void setDefaultNullable(@NotNull String defaultNullable);

  public abstract @NotNull String getDefaultNotNull();

  /**
   * Returns an annotation which marks given element as NotNull, if any. Usage of this method is discouraged.
   * Use {@link #findEffectiveNullabilityInfo(PsiModifierListOwner)} instead.
   */
  public @Nullable PsiAnnotation getNotNullAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullityAnnotationWithDefault(owner, checkBases, false);
  }

  public @Nullable PsiAnnotation copyNotNullAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo info = findOwnNullabilityInfo(original);
    if (info == null || info.getNullability() != Nullability.NOT_NULL) return null;
    return copyAnnotation(info.getAnnotation(), generated);
  }

  public @Nullable PsiAnnotation copyNullableAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo info = findOwnNullabilityInfo(original);
    if (info == null || info.getNullability() != Nullability.NULLABLE) return null;
    return copyAnnotation(info.getAnnotation(), generated);
  }

  public @Nullable PsiAnnotation copyNullableOrNotNullAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    NullabilityAnnotationInfo src = findOwnNullabilityInfo(original);
    if (src == null) return null;
    NullabilityAnnotationInfo effective = findEffectiveNullabilityInfo(generated);
    if (effective != null && effective.getNullability() == src.getNullability()) return null;
    return copyAnnotation(src.getAnnotation(), generated);
  }

  private static @Nullable PsiAnnotation copyAnnotation(@NotNull PsiAnnotation annotation, @NotNull PsiModifierListOwner target) {
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName != null) {
      if (JavaPsiFacade.getInstance(annotation.getProject()).findClass(qualifiedName, target.getResolveScope()) == null) {
        return null;
      }

      // type annotations are part of target's type and should not to be copied explicitly to avoid duplication
      if (!AnnotationTargetUtil.isTypeAnnotation(annotation)) {

        PsiModifierList modifierList = target.getModifierList();
        if (modifierList != null && !modifierList.hasAnnotation(qualifiedName)) {
          return modifierList.addAnnotation(qualifiedName);
        }
      }
    }

    return null;
  }

  /** @deprecated use {@link #copyNotNullAnnotation(PsiModifierListOwner, PsiModifierListOwner)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public PsiAnnotation copyNotNullAnnotation(@NotNull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo info = findOwnNullabilityInfo(owner);
    if (info == null || info.getNullability() != Nullability.NOT_NULL) return null;
    String qualifiedName = info.getAnnotation().getQualifiedName();
    return qualifiedName != null
           ? JavaPsiFacade.getElementFactory(owner.getProject()).createAnnotationFromText("@" + qualifiedName, owner)
           : null;
  }

  public abstract void setDefaultNotNull(@NotNull String defaultNotNull);

  private @Nullable PsiAnnotation findNullityAnnotationWithDefault(@NotNull PsiModifierListOwner owner, boolean checkBases, boolean nullable) {
    PsiAnnotation annotation = findPlainNullityAnnotation(owner, checkBases);
    if (annotation != null) {
      String qName = annotation.getQualifiedName();
      if (qName == null) return null;

      List<String> contradictory = nullable ? getNotNullsWithNickNames() : getNullablesWithNickNames();
      if (contradictory.contains(qName)) return null;

      return annotation;
    }

    PsiType type = getOwnerType(owner);
    if (type == null || TypeConversionUtil.isPrimitiveAndNotNull(type)) return null;

    // even if javax.annotation.Nullable is not configured, it should still take precedence over ByDefault annotations
    List<String> annotations = nullable ? getDefaultNotNulls() : getDefaultNullables();
    int flags = (checkBases ? CHECK_HIERARCHY : 0) | CHECK_EXTERNAL | CHECK_INFERRED | CHECK_TYPE;
    if (isAnnotated(owner, annotations, flags)) {
      return null;
    }

    if (!nullable && hasHardcodedContracts(owner)) {
      return null;
    }

    if (owner instanceof PsiParameter && !nullable && checkBases) {
      List<PsiParameter> superParameters = getSuperAnnotationOwners((PsiParameter)owner);
      if (!superParameters.isEmpty()) {
        return takeAnnotationFromSuperParameters((PsiParameter)owner, superParameters);
      }
    }

    NullabilityAnnotationInfo nullityDefault = findNullityDefaultInHierarchy(owner);
    Nullability wantedNullability = nullable ? Nullability.NULLABLE : Nullability.NOT_NULL;
    return nullityDefault != null && nullityDefault.getNullability() == wantedNullability ? nullityDefault.getAnnotation() : null;
  }

  /**
   * Returns own nullability annotation info for given element. Returned annotation is not inherited and
   * not container annotation for class/package. Still it could be inferred or external.
   *
   * @param owner element to find a nullability info for
   * @return own nullability annotation info.
   */
  public @Nullable NullabilityAnnotationInfo findOwnNullabilityInfo(@NotNull PsiModifierListOwner owner) {
    PsiType type = getOwnerType(owner);
    if (type == null || TypeConversionUtil.isPrimitiveAndNotNull(type)) return null;

    List<String> nullables = getNullablesWithNickNames();
    PsiAnnotation annotation = findPlainNullityAnnotation(owner, false);
    if (annotation != null) {
      return new NullabilityAnnotationInfo(annotation,
                                           nullables.contains(annotation.getQualifiedName()) ? Nullability.NULLABLE : Nullability.NOT_NULL,
                                           false);
    }
    return null;
  }

  /**
   * Returns information about explicit nullability annotation (without looking into external/inferred annotations, 
   * but looking into container annotations). This method is rarely useful in client code, it's designed mostly 
   * to aid the inference procedure.
   *
   * @param owner element to get the info about
   * @return the annotation info or null if no explicit annotation found
   */
  public @Nullable NullabilityAnnotationInfo findExplicitNullability(PsiModifierListOwner owner) {
    PsiAnnotation annotation = findPlainAnnotation(owner, getAllNullabilityAnnotationsWithNickNames(), false, true);
    if (annotation != null) {
      Nullability nullability =
        getNullablesWithNickNames().contains(annotation.getQualifiedName()) ? Nullability.NULLABLE : Nullability.NOT_NULL;
      return new NullabilityAnnotationInfo(annotation, nullability, false);
    }
    return findNullityDefaultInHierarchy(owner);
  }

  /**
   * Returns nullability annotation info which has effect for given element.
   *
   * @param owner element to find an annotation for
   * @return effective nullability annotation info, or null if not found.
   */
  public @Nullable NullabilityAnnotationInfo findEffectiveNullabilityInfo(@NotNull PsiModifierListOwner owner) {
    PsiType type = getOwnerType(owner);
    if (type == null || TypeConversionUtil.isPrimitiveAndNotNull(type)) return null;

    return CachedValuesManager.getCachedValue(owner, () -> CachedValueProvider.Result
      .create(doFindEffectiveNullabilityAnnotation(owner), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private @Nullable NullabilityAnnotationInfo doFindEffectiveNullabilityAnnotation(@NotNull PsiModifierListOwner owner) {
    Set<String> annotationNames = getAllNullabilityAnnotationsWithNickNames();
    Set<String> extraAnnotations = new HashSet<>(getAllDefaultAnnotations());
    extraAnnotations.addAll(annotationNames);

    PsiAnnotation annotation = findPlainAnnotation(owner, extraAnnotations, true, false);
    if (annotation != null) {
      if (!annotationNames.contains(annotation.getQualifiedName())) {
        // Deliberately excluded known standard annotation still has precedence over default class-level or package-level annotation:
        // return null in this case
        return null;
      }
      List<String> nullables = getNullablesWithNickNames();
      return new NullabilityAnnotationInfo(annotation,
                                           nullables.contains(annotation.getQualifiedName()) ? Nullability.NULLABLE : Nullability.NOT_NULL,
                                           false);
    }

    if (owner instanceof PsiParameter) {
      List<PsiParameter> superParameters = getSuperAnnotationOwners((PsiParameter)owner);
      if (!superParameters.isEmpty()) {
        for (PsiParameter parameter: superParameters) {
          PsiAnnotation plain = findPlainAnnotation(parameter, extraAnnotations, false, false);
          // Plain not null annotation is not inherited
          if (plain != null) return null;
          NullabilityAnnotationInfo defaultInfo = findNullityDefaultInHierarchy(parameter);
          if (defaultInfo != null) {
            return defaultInfo.getNullability() == Nullability.NOT_NULL ? defaultInfo : null;
          }
        }
        return null;
      }
    }

    NullabilityAnnotationInfo defaultInfo = findNullityDefaultInHierarchy(owner);
    if (defaultInfo != null && (defaultInfo.getNullability() == Nullability.NULLABLE || !hasHardcodedContracts(owner))) {
      return defaultInfo;
    }
    return null;
  }

  private PsiAnnotation takeAnnotationFromSuperParameters(@NotNull PsiParameter owner, @NotNull List<? extends PsiParameter> superOwners) {
    return RecursionManager.doPreventingRecursion(owner, true, () -> {
      for (PsiParameter superOwner : superOwners) {
        PsiAnnotation anno = findNullityAnnotationWithDefault(superOwner, false, false);
        if (anno != null) return anno;
      }
      return null;
    });
  }

  private PsiAnnotation findPlainNullityAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Set<String> qNames = getAllNullabilityAnnotationsWithNickNames();
    return findPlainAnnotation(owner, qNames, checkBases, false);
  }

  /**
   * @return an annotation (if any) with the given nullability semantics on the given declaration or its type. In case of conflicts,
   * type annotations are preferred.
   */
  public @Nullable PsiAnnotation findExplicitNullabilityAnnotation(@NotNull PsiModifierListOwner owner, @NotNull Nullability nullability) {
    if (nullability == Nullability.UNKNOWN) return null;
    List<String> names = nullability == Nullability.NULLABLE ? getNullablesWithNickNames() : getNotNullsWithNickNames();
    return findPlainAnnotation(owner, new HashSet<>(names), false, false);
  }

  private static @Nullable PsiAnnotation findPlainAnnotation(@NotNull PsiModifierListOwner owner,
                                                             @NotNull Set<String> qualifiedNames, boolean checkBases, boolean skipExternal) {
    PsiAnnotation memberAnno = checkBases && owner instanceof PsiMethod
                               ? findAnnotationInHierarchy(owner, qualifiedNames, skipExternal)
                               : findAnnotation(owner, qualifiedNames, skipExternal);
    PsiType type = getOwnerType(owner);
    if (memberAnno != null && type instanceof PsiArrayType && AnnotationTargetUtil.isTypeAnnotation(memberAnno)) {
      // Ambiguous TYPE_USE annotation on array type: we consider that it annotates an array component instead. 
      memberAnno = null;
    }
    if (memberAnno != null) {
      PsiAnnotation annotation = preferTypeAnnotation(memberAnno, type);
      if (annotation != memberAnno && !qualifiedNames.contains(annotation.getQualifiedName())) return null;
      return annotation;
    }
    if (type instanceof PsiPrimitiveType) return null;
    return findAnnotationInTypeHierarchy(type, qualifiedNames);
  }

  private static @NotNull PsiAnnotation preferTypeAnnotation(@NotNull PsiAnnotation memberAnno, @Nullable PsiType type) {
    if (type != null) {
      for (PsiAnnotation typeAnno : type.getApplicableAnnotations()) {
        if (areDifferentNullityAnnotations(memberAnno, typeAnno)) {
          return typeAnno;
        }
      }
    }
    return memberAnno;
  }

  private static boolean areDifferentNullityAnnotations(@NotNull PsiAnnotation memberAnno, @NotNull PsiAnnotation typeAnno) {
    NullableNotNullManager manager = getInstance(memberAnno.getProject());
    List<String> notNulls = manager.getNotNullsWithNickNames();
    List<String> nullables = manager.getNullablesWithNickNames();
    return nullables.contains(typeAnno.getQualifiedName()) && notNulls.contains(memberAnno.getQualifiedName()) ||
           nullables.contains(memberAnno.getQualifiedName()) && notNulls.contains(typeAnno.getQualifiedName());
  }

  protected @NotNull List<String> getNullablesWithNickNames() {
    return getNullables();
  }

  protected @NotNull List<String> getNotNullsWithNickNames() {
    return getNotNulls();
  }

  protected @NotNull Set<String> getAllNullabilityAnnotationsWithNickNames() {
    Set<String> qNames = new HashSet<>(getNullablesWithNickNames());
    qNames.addAll(getNotNullsWithNickNames());
    return Collections.unmodifiableSet(qNames);
  }

  protected boolean hasHardcodedContracts(@NotNull PsiElement element) {
    return false;
  }

  private static @Nullable PsiType getOwnerType(@NotNull PsiModifierListOwner owner) {
    if (owner instanceof PsiVariable) return ((PsiVariable)owner).getType();
    if (owner instanceof PsiMethod) return ((PsiMethod)owner).getReturnType();
    return null;
  }

  public boolean isNullable(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullityAnnotationWithDefault(owner, checkBases, true) != null;
  }

  public boolean isNotNull(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullityAnnotationWithDefault(owner, checkBases, false) != null;
  }

  /**
   * @param context place in PSI tree
   * @return default nullability for type-use elements at given place 
   */
  public @Nullable NullabilityAnnotationInfo findDefaultTypeUseNullability(@Nullable PsiElement context) {
    if (context == null) return null;
    if (context.getParent() instanceof PsiTypeElement && context.getParent().getParent() instanceof PsiLocalVariable) {
      return null;
    }
    return findNullabilityDefault(context, PsiAnnotation.TargetType.TYPE_USE);
  }

  @Nullable NullabilityAnnotationInfo findNullityDefaultInHierarchy(@NotNull PsiModifierListOwner owner) {
    return findNullabilityDefault(owner, AnnotationTargetUtil.getTargetsForLocation(owner.getModifierList()));
  }

  private @Nullable NullabilityAnnotationInfo findNullabilityDefault(@NotNull PsiElement place,
                                                                     @NotNull PsiAnnotation.TargetType @NotNull ... placeTargetTypes) {
    PsiElement element = place.getParent();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        NullabilityAnnotationInfo result = getNullityDefault((PsiModifierListOwner)element, placeTargetTypes, place, false);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PsiClassOwner) {
        String packageName = ((PsiClassOwner)element).getPackageName();
        return findNullityDefaultOnPackage(placeTargetTypes, JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName),
                                           place);
      }

      element = element.getContext();
    }
    return null;
  }

  private @Nullable NullabilityAnnotationInfo findNullityDefaultOnPackage(PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                          @Nullable PsiPackage psiPackage,
                                                                          PsiElement context) {
    boolean superPackage = false;
    while (psiPackage != null) {
      NullabilityAnnotationInfo onPkg = getNullityDefault(psiPackage, placeTargetTypes, context, superPackage);
      if (onPkg != null) return onPkg;
      superPackage = true;
      psiPackage = psiPackage.getParentPackage();
    }
    return null;
  }

  abstract @Nullable NullabilityAnnotationInfo getNullityDefault(@NotNull PsiModifierListOwner container,
                                                                 PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                 PsiElement context, boolean superPackage);

  public abstract @NotNull List<String> getNullables();

  public abstract @NotNull List<String> getNotNulls();

  /**
   * Returns true if given element is known to be nullable
   *
   * @param owner element to check
   * @return true if given element is known to be nullable
   */
  public static boolean isNullable(@NotNull PsiModifierListOwner owner) {
    return getNullability(owner) == Nullability.NULLABLE;
  }

  /**
   * Returns true if given element is known to be non-nullable
   *
   * @param owner element to check
   * @return true if given element is known to be non-nullable
   */
  public static boolean isNotNull(@NotNull PsiModifierListOwner owner) {
    return getNullability(owner) == Nullability.NOT_NULL;
  }

  /**
   * Returns nullability of given element defined by annotations.
   *
   * @param owner element to find nullability for
   * @return found nullability; {@link Nullability#UNKNOWN} if not specified or non-applicable
   */
  public static @NotNull Nullability getNullability(@NotNull PsiModifierListOwner owner) {
    NullabilityAnnotationInfo info = getInstance(owner.getProject()).findEffectiveNullabilityInfo(owner);
    return info == null ? Nullability.UNKNOWN : info.getNullability();
  }

  public abstract @NotNull List<String> getInstrumentedNotNulls();
  
  public abstract void setInstrumentedNotNulls(@NotNull List<String> names);

  /**
   * Checks if given annotation specifies the nullability (either nullable or not-null)
   * @param annotation annotation to check
   * @return true if given annotation specifies nullability
   */
  public static boolean isNullabilityAnnotation(@NotNull PsiAnnotation annotation) {
    return getInstance(annotation.getProject()).getAllNullabilityAnnotationsWithNickNames().contains(annotation.getQualifiedName());
  }
}