// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_INFERRED;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * @author anna
 * @since 25.01.2011
 */
public abstract class NullableNotNullManager {
  protected static final Logger LOG = Logger.getInstance(NullableNotNullManager.class);
  protected final Project myProject;

  public String myDefaultNullable = AnnotationUtil.NULLABLE;
  public String myDefaultNotNull = AnnotationUtil.NOT_NULL;
  @SuppressWarnings("deprecation") public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
  @SuppressWarnings("deprecation") public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();

  private static final String JAVAX_ANNOTATION_NULLABLE = "javax.annotation.Nullable";
  protected static final String JAVAX_ANNOTATION_NONNULL = "javax.annotation.Nonnull";

  static final String[] DEFAULT_NULLABLES = {
    AnnotationUtil.NULLABLE,
    JAVAX_ANNOTATION_NULLABLE,
    "javax.annotation.CheckForNull",
    "edu.umd.cs.findbugs.annotations.Nullable",
    "android.support.annotation.Nullable"
  };

  public NullableNotNullManager(Project project) {
    myProject = project;
    Collections.addAll(myNullables, DEFAULT_NULLABLES);
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

  private static void addAllIfNotPresent(@NotNull Collection<String> collection, @NotNull String... annotations) {
    for (String annotation : annotations) {
      LOG.assertTrue(annotation != null);
      if (!collection.contains(annotation)) {
        collection.add(annotation);
      }
    }
  }

  public void setNotNulls(@NotNull String... annotations) {
    myNotNulls.clear();
    for (String annotation : getPredefinedNotNulls()) {
      LOG.assertTrue(annotation != null);
      if (!myNotNulls.contains(annotation)) {
        myNotNulls.add(annotation);
      }
    }
    addAllIfNotPresent(myNotNulls, annotations);
  }

  public void setNullables(@NotNull String... annotations) {
    myNullables.clear();
    addAllIfNotPresent(myNullables, DEFAULT_NULLABLES);
    addAllIfNotPresent(myNullables, annotations);
  }

  @NotNull
  public String getDefaultNullable() {
    return myDefaultNullable;
  }

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
    return findNullabilityAnnotationWithDefault(owner, checkBases, true);
  }

  public boolean isContainerAnnotation(@NotNull PsiAnnotation anno) {
    PsiAnnotation.TargetType[] acceptAnyTarget = PsiAnnotation.TargetType.values();
    return isNullabilityDefault(anno, true, acceptAnyTarget) || isNullabilityDefault(anno, false, acceptAnyTarget);
  }

  public void setDefaultNullable(@NotNull String defaultNullable) {
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myDefaultNullable = defaultNullable;
  }

  @NotNull
  public String getDefaultNotNull() {
    return myDefaultNotNull;
  }

  @Nullable
  public PsiAnnotation getNotNullAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullabilityAnnotationWithDefault(owner, checkBases, false);
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
        if (modifierList != null && modifierList.findAnnotation(qualifiedName) == null) {
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

  public void setDefaultNotNull(@NotNull String defaultNotNull) {
    LOG.assertTrue(getNotNulls().contains(defaultNotNull));
    myDefaultNotNull = defaultNotNull;
  }

  @Nullable
  private PsiAnnotation findNullabilityAnnotationWithDefault(@NotNull PsiModifierListOwner owner, boolean checkBases, boolean nullable) {
    PsiAnnotation annotation = findPlainNullabilityAnnotation(owner, checkBases);
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
    List<String> annotations = nullable ? getPredefinedNotNulls() : Arrays.asList(DEFAULT_NULLABLES);
    int flags = (checkBases ? CHECK_HIERARCHY : 0) | CHECK_EXTERNAL | CHECK_INFERRED | CHECK_TYPE;
    if (AnnotationUtil.isAnnotated(owner, annotations, flags)) {
      return null;
    }

    if (!nullable && hasHardcodedContracts(owner)) {
      return null;
    }

    if (owner instanceof PsiParameter && !nullable && checkBases) {
      List<PsiParameter> superParameters = AnnotationUtil.getSuperAnnotationOwners((PsiParameter)owner);
      if (!superParameters.isEmpty()) {
        return takeAnnotationFromSuperParameters((PsiParameter)owner, superParameters);
      }
    }

    return findNullabilityDefaultInHierarchy(owner, nullable);
  }

  private PsiAnnotation takeAnnotationFromSuperParameters(@NotNull PsiParameter owner, final List<PsiParameter> superOwners) {
    return RecursionManager.doPreventingRecursion(owner, true, () -> {
      for (PsiParameter superOwner : superOwners) {
        PsiAnnotation anno = findNullabilityAnnotationWithDefault(superOwner, false, false);
        if (anno != null) return anno;
      }
      return null;
    });
  }

  private PsiAnnotation findPlainNullabilityAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    Set<String> qNames = ContainerUtil.newHashSet(getNullablesWithNickNames());
    qNames.addAll(getNotNullsWithNickNames());
    PsiAnnotation memberAnno = checkBases && owner instanceof PsiMethod
                               ? AnnotationUtil.findAnnotationInHierarchy(owner, qNames)
                               : AnnotationUtil.findAnnotation(owner, qNames);
    PsiType type = getOwnerType(owner);
    if (memberAnno != null) {
      return preferTypeAnnotation(memberAnno, type);
    }
    if (type != null) {
      return ContainerUtil.find(type.getAnnotations(), a -> qNames.contains(a.getQualifiedName()));
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
    return findNullabilityAnnotationWithDefault(owner, checkBases, true) != null;
  }

  public boolean isNotNull(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullabilityAnnotationWithDefault(owner, checkBases, false) != null;
  }

  @Nullable
  static PsiAnnotation findNullabilityDefaultInHierarchy(PsiModifierListOwner owner, boolean nullable) {
    PsiAnnotation.TargetType[] placeTargetTypes = AnnotationTargetUtil.getTargetsForLocation(owner.getModifierList());

    PsiElement element = owner.getParent();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        PsiAnnotation annotation = getNullabilityDefault((PsiModifierListOwner)element, nullable, placeTargetTypes);
        if (annotation != null) {
          return annotation;
        }
      }

      if (element instanceof PsiClassOwner) {
        String packageName = ((PsiClassOwner)element).getPackageName();
        PsiPackage psiPackage = JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName);
        return psiPackage == null ? null : getNullabilityDefault(psiPackage, nullable, placeTargetTypes);
      }

      element = element.getContext();
    }
    return null;
  }

  private static PsiAnnotation getNullabilityDefault(@NotNull PsiModifierListOwner container, boolean nullable, PsiAnnotation.TargetType[] placeTargetTypes) {
    PsiModifierList modifierList = container.getModifierList();
    if (modifierList == null) return null;
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (isNullabilityDefault(annotation, nullable, placeTargetTypes)) {
        return annotation;
      }
    }
    return null;
  }

  private static boolean isNullabilityDefault(@NotNull PsiAnnotation annotation, boolean nullable, PsiAnnotation.TargetType[] placeTargetTypes) {
    PsiJavaCodeReferenceElement element = annotation.getNameReferenceElement();
    PsiElement declaration = element == null ? null : element.resolve();
    if (!(declaration instanceof PsiClass)) return false;

    String fqn = nullable ? JAVAX_ANNOTATION_NULLABLE : JAVAX_ANNOTATION_NONNULL;
    if (!AnnotationUtil.isAnnotated((PsiClass)declaration, fqn, CHECK_TYPE)) return false;

    PsiAnnotation tqDefault = AnnotationUtil.findAnnotation((PsiClass)declaration, true, "javax.annotation.meta.TypeQualifierDefault");
    if (tqDefault == null) return false;

    Set<PsiAnnotation.TargetType> required = AnnotationTargetUtil.extractRequiredAnnotationTargets(tqDefault.findAttributeValue(null));
    return required != null && (required.isEmpty() || ContainerUtil.intersects(required, Arrays.asList(placeTargetTypes)));
  }

  @NotNull
  public List<String> getNullables() {
    return myNullables;
  }

  @NotNull
  public List<String> getNotNulls() {
    return myNotNulls;
  }

  boolean hasDefaultValues() {
    List<String> predefinedNotNulls = getPredefinedNotNulls();
    if (DEFAULT_NULLABLES.length != getNullables().size() || predefinedNotNulls.size() != getNotNulls().size()) {
      return false;
    }
    if (!myDefaultNotNull.equals(AnnotationUtil.NOT_NULL) || !myDefaultNullable.equals(AnnotationUtil.NULLABLE)) {
      return false;
    }
    for (int i = 0; i < DEFAULT_NULLABLES.length; i++) {
      if (!getNullables().get(i).equals(DEFAULT_NULLABLES[i])) {
        return false;
      }
    }
    for (int i = 0; i < predefinedNotNulls.size(); i++) {
      if (!getNotNulls().get(i).equals(predefinedNotNulls.get(i))) {
        return false;
      }
    }

    return true;
  }

  public static boolean isNullable(@NotNull PsiModifierListOwner owner) {
    return getInstance(owner.getProject()).isNullable(owner, true);
  }

  public static boolean isNotNull(@NotNull PsiModifierListOwner owner) {
    return getInstance(owner.getProject()).isNotNull(owner, true);
  }

  public abstract List<String> getPredefinedNotNulls();

  public static boolean isNullableAnnotation(@NotNull PsiAnnotation annotation) {
    return getInstance(annotation.getProject()).getNullablesWithNickNames().contains(annotation.getQualifiedName());
  }

  public static boolean isNotNullAnnotation(@NotNull PsiAnnotation annotation) {
    return getInstance(annotation.getProject()).getNotNullsWithNickNames().contains(annotation.getQualifiedName());
  }
}