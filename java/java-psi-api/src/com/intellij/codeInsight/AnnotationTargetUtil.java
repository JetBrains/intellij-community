/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public class AnnotationTargetUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.AnnotationUtil");

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
  private static final TargetType[] PARAMETER_TARGETS = {TargetType.PARAMETER, TargetType.TYPE_USE};
  private static final TargetType[] LOCAL_VARIABLE_TARGETS = {TargetType.LOCAL_VARIABLE, TargetType.TYPE_USE};

  @NotNull
  public static TargetType[] getTargetsForLocation(@Nullable PsiAnnotationOwner owner) {
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
        if (((PsiClass)element).isAnnotationType()) {
          return ANNOTATION_TARGETS;
        }
        else {
          return TYPE_TARGETS;
        }
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
    }

    return TargetType.EMPTY_ARRAY;
  }

  @Nullable
  public static Set<TargetType> extractRequiredAnnotationTargets(@Nullable PsiAnnotationMemberValue value) {
    if (value instanceof PsiReference) {
      TargetType targetType = translateTargetRef((PsiReference)value);
      if (targetType != null) {
        return Collections.singleton(targetType);
      }
    }
    else if (value instanceof PsiArrayInitializerMemberValue) {
      Set <TargetType> targets = ContainerUtil.newHashSet();
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

  @Nullable
  private static TargetType translateTargetRef(@NotNull PsiReference reference) {
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
  @Nullable
  public static TargetType findAnnotationTarget(@NotNull PsiAnnotation annotation, @NotNull TargetType... types) {
    if (types.length != 0) {
      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      if (ref != null) {
        PsiElement annotationType = ref.resolve();
        if (annotationType instanceof PsiClass) {
          return findAnnotationTarget((PsiClass)annotationType, types);
        }
      }
    }

    return TargetType.UNKNOWN;
  }

  /**
   * From given targets, returns first where the annotation may be applied. Returns {@code null} when the annotation is not applicable
   * at any of the targets, or {@linkplain TargetType#UNKNOWN} if the type is not a valid annotation (e.g. cannot be resolved).
   */
  @Nullable
  public static TargetType findAnnotationTarget(@NotNull PsiClass annotationType, @NotNull TargetType... types) {
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
  @Nullable
  public static Set<TargetType> getAnnotationTargets(@NotNull PsiClass annotationType) {
    if (!annotationType.isAnnotationType()) return null;
    PsiModifierList modifierList = annotationType.getModifierList();
    if (modifierList == null) return null;
    PsiAnnotation target = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
    if (target == null) return DEFAULT_TARGETS;  // if omitted it is applicable to all but Java 8 TYPE_USE/TYPE_PARAMETERS targets

    return extractRequiredAnnotationTargets(target.findAttributeValue(null));
  }
}