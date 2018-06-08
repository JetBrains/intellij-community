// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.AnnotationUtil.*;

/**
 * @author anna
 * @since 25.01.2011
 */
public abstract class NullableNotNullManager {
  protected static final Logger LOG = Logger.getInstance(NullableNotNullManager.class);
  protected final Project myProject;

  protected static final String JAVAX_ANNOTATION_NULLABLE = "javax.annotation.Nullable";
  protected static final String JAVAX_ANNOTATION_NONNULL = "javax.annotation.Nonnull";

  static final String[] DEFAULT_NULLABLES = {
    NULLABLE,
    JAVAX_ANNOTATION_NULLABLE,
    "javax.annotation.CheckForNull",
    "edu.umd.cs.findbugs.annotations.Nullable",
    "android.support.annotation.Nullable",
    "androidx.annotation.Nullable",
    "org.checkerframework.checker.nullness.qual.Nullable",
    "org.checkerframework.checker.nullness.compatqual.NullableDecl",
    "org.checkerframework.checker.nullness.compatqual.NullableType"
  };
  static final String[] DEFAULT_NOT_NULLS = {
    NotNull.class.getName(),
    "javax.annotation.Nonnull",
    "javax.validation.constraints.NotNull",
    "edu.umd.cs.findbugs.annotations.NonNull",
    "android.support.annotation.NonNull",
    "androidx.annotation.NonNull",
    "org.checkerframework.checker.nullness.qual.NonNull",
    "org.checkerframework.checker.nullness.compatqual.NonNullDecl",
    "org.checkerframework.checker.nullness.compatqual.NonNullType"
  };
  private static final List<String> DEFAULT_ALL = Arrays.asList(ArrayUtil.mergeArrays(DEFAULT_NULLABLES, DEFAULT_NOT_NULLS));

  protected NullableNotNullManager(Project project) {
    myProject = project;
  }

  public static NullableNotNullManager getInstance(Project project) {
    return ServiceManager.getService(project, NullableNotNullManager.class);
  }

  /**
   * @return if owner has a @NotNull or @Nullable annotation, or is in scope of @ParametersAreNullableByDefault or ParametersAreNonnullByDefault
   */
  public boolean hasNullability(@NotNull PsiModifierListOwner owner) {
    return isNullable(owner, false) || isNotNull(owner, false);
  }

  public abstract void setNotNulls(@NotNull String... annotations);

  public abstract void setNullables(@NotNull String... annotations);

  @NotNull
  public abstract String getDefaultNullable();

  @Nullable
  public String getNullable(@NotNull PsiModifierListOwner owner) {
    PsiAnnotation annotation = getNullableAnnotation(owner, false);
    return annotation == null ? null : annotation.getQualifiedName();
  }

  private String checkContainer(PsiAnnotation annotation) {
    if (annotation == null) {
      return null;
    }
    if (isContainerAnnotation(annotation)) {
      return null;
    }
    return annotation.getQualifiedName();
  }

  @Nullable
  public PsiAnnotation getNullableAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullityAnnotationWithDefault(owner, checkBases, true);
  }

  public boolean isContainerAnnotation(@NotNull PsiAnnotation anno) {
    PsiAnnotation.TargetType[] acceptAnyTarget = PsiAnnotation.TargetType.values();
    return checkNullityDefault(anno, acceptAnyTarget, false) != null;
  }

  public abstract void setDefaultNullable(@NotNull String defaultNullable);

  @NotNull
  public abstract String getDefaultNotNull();

  @Nullable
  public PsiAnnotation getNotNullAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullityAnnotationWithDefault(owner, checkBases, false);
  }

  @Nullable
  public PsiAnnotation copyNotNullAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    return copyAnnotation(getNotNullAnnotation(original, false), generated);
  }

  @Nullable
  public PsiAnnotation copyNullableAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    return copyAnnotation(getNullableAnnotation(original, false), generated);
  }

  @Nullable
  public PsiAnnotation copyNullableOrNotNullAnnotation(@NotNull PsiModifierListOwner original, @NotNull PsiModifierListOwner generated) {
    PsiAnnotation annotation = getNullableAnnotation(original, false);
    if (annotation == null) annotation = getNotNullAnnotation(original, false);
    return copyAnnotation(annotation, generated);
  }

  @Nullable
  private PsiAnnotation copyAnnotation(PsiAnnotation annotation, PsiModifierListOwner target) {
    // type annotations are part of target's type and should not to be copied explicitly to avoid duplication
    if (annotation != null && !AnnotationTargetUtil.isTypeAnnotation(annotation)) {
      String qualifiedName = checkContainer(annotation);
      if (qualifiedName != null) {
        PsiModifierList modifierList = target.getModifierList();
        if (modifierList != null && !modifierList.hasAnnotation(qualifiedName)) {
          return modifierList.addAnnotation(qualifiedName);
        }
      }
    }

    return null;
  }

  /** @deprecated use {@link #copyNotNullAnnotation(PsiModifierListOwner, PsiModifierListOwner)} (to be removed in IDEA 17) */
  public PsiAnnotation copyNotNullAnnotation(PsiModifierListOwner owner) {
    return copyAnnotation(owner, getNotNullAnnotation(owner, false));
  }

  /** @deprecated use {@link #copyNullableOrNotNullAnnotation(PsiModifierListOwner, PsiModifierListOwner)} (to be removed in IDEA 17) */
  public PsiAnnotation copyNullableAnnotation(PsiModifierListOwner owner) {
    return copyAnnotation(owner, getNullableAnnotation(owner, false));
  }

  private PsiAnnotation copyAnnotation(PsiModifierListOwner owner, PsiAnnotation annotation) {
    String notNull = checkContainer(annotation);
    return notNull != null ? JavaPsiFacade.getElementFactory(owner.getProject()).createAnnotationFromText("@" + notNull, owner) : null;
  }

  @Nullable
  public String getNotNull(@NotNull PsiModifierListOwner owner) {
    PsiAnnotation annotation = getNotNullAnnotation(owner, false);
    return annotation == null ? null : annotation.getQualifiedName();
  }

  public abstract void setDefaultNotNull(@NotNull String defaultNotNull);

  @Nullable
  private PsiAnnotation findNullityAnnotationWithDefault(@NotNull PsiModifierListOwner owner, boolean checkBases, boolean nullable) {
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
    List<String> annotations = Arrays.asList(nullable ? DEFAULT_NOT_NULLS : DEFAULT_NULLABLES);
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

    NullityDefault nullityDefault = findNullityDefaultInHierarchy(owner);
    return nullityDefault != null && nullityDefault.isNullableDefault == nullable ? nullityDefault.annotation : null;
  }

  /**
   * Returns nullability annotation (either nullable, or non-nullable) which has effect for given element.
   * To figure out its exact effect can be subsequently checked using {@link #isNullableAnnotation(PsiAnnotation)},
   * {@link #isNotNullAnnotation(PsiAnnotation)}, {@link #isContainerNullableAnnotation(PsiAnnotation)} or
   * {@link #isContainerNotNullAnnotation(PsiAnnotation)}. May return explicit, inferred, external or
   * default nullability annotation for class/package.
   *
   * @param owner
   * @return effective nullability annotation, or null if not found.
   */
  @Nullable
  public PsiAnnotation findEffectiveNullabilityAnnotation(@NotNull PsiModifierListOwner owner) {
    PsiType type = getOwnerType(owner);
    if (type == null || TypeConversionUtil.isPrimitiveAndNotNull(type)) return null;

    return CachedValuesManager.getCachedValue(owner, () -> CachedValueProvider.Result
      .create(doFindEffectiveNullabilityAnnotation(owner), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private PsiAnnotation doFindEffectiveNullabilityAnnotation(@NotNull PsiModifierListOwner owner) {
    Set<String> annotationNames = ContainerUtil.newHashSet(getNullablesWithNickNames());
    annotationNames.addAll(getNotNullsWithNickNames());
    Set<String> extraAnnotations = DEFAULT_ALL.stream().filter(anno -> !annotationNames.contains(anno)).collect(Collectors.toSet());
    annotationNames.addAll(extraAnnotations);

    PsiAnnotation annotation = findPlainAnnotation(owner, true, annotationNames);
    if (annotation != null) {
      if (extraAnnotations.contains(annotation.getQualifiedName())) {
        // Deliberately excluded known standard annotation still has precedence over default class-level or package-level annotation:
        // return null in this case
        return null;
      }
      return annotation;
    }

    if (owner instanceof PsiParameter) {
      List<PsiParameter> superParameters = getSuperAnnotationOwners((PsiParameter)owner);
      if (!superParameters.isEmpty()) {
        PsiAnnotation superParameterAnnotation = takeAnnotationFromSuperParameters((PsiParameter)owner, superParameters);
        return superParameterAnnotation != null && isContainerNotNullAnnotation(superParameterAnnotation) ? superParameterAnnotation : null;
      }
    }

    NullityDefault nullityDefault = findNullityDefaultInHierarchy(owner);
    if (nullityDefault != null && (nullityDefault.isNullableDefault || !hasHardcodedContracts(owner))) {
      return nullityDefault.annotation;
    }
    return null;
  }

  private PsiAnnotation takeAnnotationFromSuperParameters(@NotNull PsiParameter owner, final List<PsiParameter> superOwners) {
    return RecursionManager.doPreventingRecursion(owner, true, () -> {
      for (PsiParameter superOwner : superOwners) {
        PsiAnnotation anno = findNullityAnnotationWithDefault(superOwner, false, false);
        if (anno != null) return anno;
      }
      return null;
    });
  }

  private PsiAnnotation findPlainNullityAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Set<String> qNames = ContainerUtil.newHashSet(getNullablesWithNickNames());
    qNames.addAll(getNotNullsWithNickNames());
    return findPlainAnnotation(owner, checkBases, qNames);
  }

  @Nullable
  private static PsiAnnotation findPlainAnnotation(@NotNull PsiModifierListOwner owner,
                                                   boolean checkBases,
                                                   Set<String> qualifiedNames) {
    PsiAnnotation memberAnno = checkBases && owner instanceof PsiMethod
                               ? findAnnotationInHierarchy(owner, qualifiedNames)
                               : findAnnotation(owner, qualifiedNames);
    PsiType type = getOwnerType(owner);
    if (memberAnno != null) {
      return preferTypeAnnotation(memberAnno, type);
    }
    if (type != null) {
      return ContainerUtil.find(type.getAnnotations(), a -> qualifiedNames.contains(a.getQualifiedName()));
    }
    return null;
  }

  private static PsiAnnotation preferTypeAnnotation(@NotNull PsiAnnotation memberAnno, @Nullable PsiType type) {
    if (type != null) {
      for (PsiAnnotation typeAnno : type.getApplicableAnnotations()) {
        if (areDifferentNullityAnnotations(memberAnno, typeAnno)) {
          return typeAnno;
        }
      }
    }
    return memberAnno;
  }

  private static boolean areDifferentNullityAnnotations(@NotNull PsiAnnotation memberAnno, PsiAnnotation typeAnno) {
    return isNullableAnnotation(typeAnno) && isNotNullAnnotation(memberAnno) ||
        isNullableAnnotation(memberAnno) && isNotNullAnnotation(typeAnno);
  }

  @NotNull
  protected List<String> getNullablesWithNickNames() {
    return getNullables();
  }

  @NotNull
  protected List<String> getNotNullsWithNickNames() {
    return getNotNulls();
  }

  protected boolean hasHardcodedContracts(PsiElement element) {
    return false;
  }

  @Nullable
  private static PsiType getOwnerType(PsiModifierListOwner owner) {
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

  @Nullable
  NullityDefault findNullityDefaultInHierarchy(@NotNull PsiModifierListOwner owner) {
    PsiAnnotation.TargetType[] placeTargetTypes = AnnotationTargetUtil.getTargetsForLocation(owner.getModifierList());

    PsiElement element = owner.getParent();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        NullityDefault result = getNullityDefault((PsiModifierListOwner)element, placeTargetTypes, false);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PsiClassOwner) {
        String packageName = ((PsiClassOwner)element).getPackageName();
        return findNullityDefaultOnPackage(placeTargetTypes,
                                           JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName));
      }

      element = element.getContext();
    }
    return null;
  }

  @Nullable
  private NullityDefault findNullityDefaultOnPackage(PsiAnnotation.TargetType[] placeTargetTypes, @Nullable PsiPackage psiPackage) {
    boolean superPackage = false;
    while (psiPackage != null) {
      NullityDefault onPkg = getNullityDefault(psiPackage, placeTargetTypes, superPackage);
      if (onPkg != null) return onPkg;
      superPackage = true;
      psiPackage = psiPackage.getParentPackage();
    }
    return null;
  }

  @Nullable
  private NullityDefault getNullityDefault(PsiModifierListOwner container, PsiAnnotation.TargetType[] placeTargetTypes, boolean superPackage) {
    PsiModifierList modifierList = container.getModifierList();
    if (modifierList == null) return null;
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      NullityDefault result = checkNullityDefault(annotation, placeTargetTypes, superPackage);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private NullityDefault checkNullityDefault(PsiAnnotation annotation, PsiAnnotation.TargetType[] placeTargetTypes, boolean superPackage) {
    NullityDefault jsr = superPackage ? null : isJsr305Default(annotation, placeTargetTypes);
    return jsr != null ? jsr : CheckerFrameworkNullityUtil.isCheckerDefault(annotation, placeTargetTypes);
  }

  @Nullable
  protected abstract NullityDefault isJsr305Default(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotation.TargetType[] placeTargetTypes);

  @NotNull
  public abstract List<String> getNullables();

  @NotNull
  public abstract List<String> getNotNulls();

  public static boolean isNullable(@NotNull PsiModifierListOwner owner) {
    return getInstance(owner.getProject()).isNullable(owner, true);
  }

  public static boolean isNotNull(@NotNull PsiModifierListOwner owner) {
    return getInstance(owner.getProject()).isNotNull(owner, true);
  }

  @NotNull
  public abstract List<String> getInstrumentedNotNulls();
  
  public abstract void setInstrumentedNotNulls(@NotNull List<String> names);

  public static boolean isNullableAnnotation(@NotNull PsiAnnotation annotation) {
    return getInstance(annotation.getProject()).getNullablesWithNickNames().contains(annotation.getQualifiedName());
  }

  public static boolean isNotNullAnnotation(@NotNull PsiAnnotation annotation) {
    return getInstance(annotation.getProject()).getNotNullsWithNickNames().contains(annotation.getQualifiedName());
  }

  public static boolean isContainerNullableAnnotation(@NotNull PsiAnnotation annotation) {
    PsiAnnotation.TargetType[] acceptAnyTarget = PsiAnnotation.TargetType.values();
    NullityDefault nullityDefault = getInstance(annotation.getProject()).checkNullityDefault(annotation, acceptAnyTarget, false);
    return nullityDefault != null && nullityDefault.isNullableDefault;
  }

  public static boolean isContainerNotNullAnnotation(@NotNull PsiAnnotation annotation) {
    PsiAnnotation.TargetType[] acceptAnyTarget = PsiAnnotation.TargetType.values();
    NullityDefault nullityDefault = getInstance(annotation.getProject()).checkNullityDefault(annotation, acceptAnyTarget, false);
    return nullityDefault != null && !nullityDefault.isNullableDefault;
  }
}