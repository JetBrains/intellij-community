// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author peter
 */
public class AnnotationTargetUtil {
  private static final Logger LOG = Logger.getInstance(AnnotationTargetUtil.class);

  public static final Set<TargetType> DEFAULT_TARGETS = ContainerUtil.immutableSet(
    TargetType.PACKAGE, TargetType.TYPE, TargetType.ANNOTATION_TYPE, TargetType.FIELD, TargetType.METHOD, TargetType.CONSTRUCTOR,
    TargetType.PARAMETER, TargetType.LOCAL_VARIABLE);

  private static final TargetType[] PACKAGE_TARGETS = {TargetType.PACKAGE};
  private static final TargetType[] TYPE_USE_TARGETS = {TargetType.TYPE_USE};
  private static final TargetType[] ANNOTATION_TARGETS = {TargetType.ANNOTATION_TYPE, TargetType.TYPE, TargetType.TYPE_USE};
  private static final TargetType[] TYPE_TARGETS = {TargetType.TYPE, TargetType.TYPE_USE};
  private static final TargetType[] TYPE_PARAMETER_TARGETS = {TargetType.TYPE_PARAMETER, TargetType.TYPE_USE};
  private static final TargetType[] CONSTRUCTOR_TARGETS = {TargetType.CONSTRUCTOR, TargetType.TYPE_USE};
  private static final TargetType[] METHOD_TARGETS = {TargetType.METHOD, TargetType.TYPE_USE};
  private static final TargetType[] FIELD_TARGETS = {TargetType.FIELD, TargetType.TYPE_USE};
  private static final TargetType[] RECORD_COMPONENT_TARGETS = {TargetType.RECORD_COMPONENT, TargetType.FIELD, TargetType.METHOD, 
    TargetType.PARAMETER, TargetType.TYPE_USE};
  private static final TargetType[] PARAMETER_TARGETS = {TargetType.PARAMETER, TargetType.TYPE_USE};
  private static final TargetType[] LOCAL_VARIABLE_TARGETS = {TargetType.LOCAL_VARIABLE, TargetType.TYPE_USE};
  private static final TargetType[] MODULE_TARGETS = {TargetType.MODULE};

  public static TargetType @NotNull [] getTargetsForLocation(@Nullable PsiAnnotationOwner owner) {
    if (owner == null) {
      return TargetType.EMPTY_ARRAY;
    }

    if (owner instanceof PsiType || owner instanceof PsiTypeElement) {
      return TYPE_USE_TARGETS;
    }

    if (owner instanceof PsiTypeParameter) {
      return TYPE_PARAMETER_TARGETS;
    }

    if (owner instanceof PsiModifierList) {
      PsiElement element = ((PsiModifierList)owner).getParent();
      if (element instanceof PsiPackageStatement) {
        return PACKAGE_TARGETS;
      }
      if (element instanceof PsiClass) {
        if (((PsiClass)element).getModifierList() != owner){
          return TargetType.EMPTY_ARRAY;
        }
        if (((PsiClass)element).isAnnotationType()) {
          return ANNOTATION_TARGETS;
        }
        else {
          return TYPE_TARGETS;
        }
      }
      if (element instanceof PsiRecordComponent) {
        return RECORD_COMPONENT_TARGETS;
      }
      if (element instanceof PsiMethod) {
        if (((PsiMethod)element).isConstructor()) {
          return CONSTRUCTOR_TARGETS;
        }
        else {
          return METHOD_TARGETS;
        }
      }
      if (element instanceof PsiField) {
        return FIELD_TARGETS;
      }
      if (element instanceof PsiParameter) {
        // PARAMETER applies only to formal parameters (methods & lambdas) and catch parameters
        // see https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.6.4.1
        PsiElement scope = element.getParent();
        if (scope instanceof PsiForeachStatement) {
          return LOCAL_VARIABLE_TARGETS;
        }
        if (scope instanceof PsiParameterList && scope.getParent() instanceof PsiLambdaExpression &&
            ((PsiParameter)element).getTypeElement() == null) {
          return TargetType.EMPTY_ARRAY;
        }

        return PARAMETER_TARGETS;
      }
      if (element instanceof PsiLocalVariable) {
        return LOCAL_VARIABLE_TARGETS;
      }
      if (element instanceof PsiReceiverParameter) {
        return TYPE_USE_TARGETS;
      }
      if (element instanceof PsiJavaModule) {
        return MODULE_TARGETS;
      }
    }

    return TargetType.EMPTY_ARRAY;
  }

  public static @Nullable Set<TargetType> extractRequiredAnnotationTargets(@Nullable PsiAnnotationMemberValue value) {
    if (value instanceof PsiReference) {
      TargetType targetType = translateTargetRef((PsiReference)value);
      if (targetType != null) {
        return Collections.singleton(targetType);
      }
    }
    else if (value instanceof PsiArrayInitializerMemberValue) {
      Set <TargetType> targets = EnumSet.noneOf(TargetType.class);
      for (PsiAnnotationMemberValue initializer : ((PsiArrayInitializerMemberValue)value).getInitializers()) {
        if (initializer instanceof PsiReference) {
          TargetType targetType = translateTargetRef((PsiReference)initializer);
          if (targetType != null) {
            targets.add(targetType);
          }
        }
      }
      return targets;
    }

    return null;
  }

  private static @Nullable TargetType translateTargetRef(@NotNull PsiReference reference) {
    if (reference instanceof PsiJavaCodeReferenceElement) {
      String name = ((PsiJavaCodeReferenceElement)reference).getReferenceName();
      if (name != null) {
        try {
          return TargetType.valueOf(name);
        }
        catch (IllegalArgumentException ignore) {
        }
      }
    }

    PsiElement field = reference.resolve();
    if (field instanceof PsiEnumConstant) {
      String name = ((PsiEnumConstant)field).getName();
      try {
        return TargetType.valueOf(name);
      }
      catch (IllegalArgumentException e) {
        LOG.warn("Unknown target: " + name);
      }
    }
    return null;
  }

  /**
   * Returns {@code true} if the annotation resolves to a class having {@link TargetType#TYPE_USE} in it's targets.
   */
  public static boolean isTypeAnnotation(@NotNull PsiAnnotation element) {
    return findAnnotationTarget(element, TargetType.TYPE_USE) == TargetType.TYPE_USE;
  }

  /**
   * From given targets, returns first where the annotation may be applied. Returns {@code null} when the annotation is not applicable
   * at any of the targets, or {@linkplain TargetType#UNKNOWN} if the annotation does not resolve to a valid annotation type.
   */
  public static @Nullable TargetType findAnnotationTarget(@NotNull PsiAnnotation annotation, TargetType @NotNull ... types) {
    if (types.length != 0) {
      PsiClass annotationType = annotation.resolveAnnotationType();
      if (annotationType != null) {
        return findAnnotationTarget(annotationType, types);
      }
    }

    return TargetType.UNKNOWN;
  }

  /**
   * From given targets, returns first where the annotation may be applied. Returns {@code null} when the annotation is not applicable
   * at any of the targets, or {@linkplain TargetType#UNKNOWN} if the type is not a valid annotation (e.g. cannot be resolved).
   */
  public static @Nullable TargetType findAnnotationTarget(@NotNull PsiClass annotationType, TargetType @NotNull ... types) {
    if (types.length != 0) {
      Set<TargetType> targets = getAnnotationTargets(annotationType);
      if (targets != null) {
        for (TargetType type : types) {
          if (type != TargetType.UNKNOWN && targets.contains(type)) {
            return type;
          }
        }
        return null;
      }
    }

    return TargetType.UNKNOWN;
  }

  /**
   * Returns a set of targets where the given annotation may be applied, or {@code null} when the type is not a valid annotation.
   */
  public static @Nullable Set<TargetType> getAnnotationTargets(@NotNull PsiClass annotationType) {
    if (!annotationType.isAnnotationType()) return null;
    PsiModifierList modifierList = annotationType.getModifierList();
    if (modifierList == null) return null;

    return CachedValuesManager.getCachedValue(modifierList, () ->
      CachedValueProvider.Result.create(calcAnnotationTargets(modifierList), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @Nullable Set<TargetType> calcAnnotationTargets(PsiModifierList modifierList) {
    PsiAnnotation target = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
    if (target == null) return DEFAULT_TARGETS;  // if omitted it is applicable to all but Java 8 TYPE_USE/TYPE_PARAMETERS targets

    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(target, null);
    if (attribute == null) return null;

    return extractRequiredAnnotationTargets(attribute.getDetachedValue());
  }

  /**
   * @param modifierListOwner modifier list owner
   * @param annotation the qualified name of the annotation to add
   * @return a target annotation owner to add the annotation (either modifier list or type element depending on the annotation target)
   * Returns null if {@code modifierListOwner.getModifierList()} is null.
   */
  public static @Nullable PsiAnnotationOwner getTarget(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String annotation) {
    PsiModifierList list = modifierListOwner.getModifierList();
    if (list == null) return null;
    PsiClass annotationClass = JavaPsiFacade.getInstance(modifierListOwner.getProject())
      .findClass(annotation, modifierListOwner.getResolveScope());
    if (annotationClass != null && findAnnotationTarget(annotationClass, TargetType.TYPE_USE) != null) {
      PsiElement parent = list.getParent();
      PsiTypeElement type = null;
      if (parent instanceof PsiMethod) {
        type = ((PsiMethod)parent).getReturnTypeElement();
      }
      else if (parent instanceof PsiVariable) {
        type = ((PsiVariable)parent).getTypeElement();
      }
      if (type != null && !type.getType().equals(PsiType.VOID)) return type;
    }
    return list;
  }
}