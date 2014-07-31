/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public class AnnotationTargetUtil {
  public static final Set<PsiAnnotation.TargetType> DEFAULT_TARGETS = Collections.unmodifiableSet(ContainerUtil.newHashSet(
    PsiAnnotation.TargetType.PACKAGE, PsiAnnotation.TargetType.TYPE, PsiAnnotation.TargetType.ANNOTATION_TYPE,
    PsiAnnotation.TargetType.FIELD, PsiAnnotation.TargetType.METHOD, PsiAnnotation.TargetType.CONSTRUCTOR,
    PsiAnnotation.TargetType.PARAMETER, PsiAnnotation.TargetType.LOCAL_VARIABLE));
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.AnnotationUtil");
  private static final PsiAnnotation.TargetType[] PACKAGE_TARGETS = {PsiAnnotation.TargetType.PACKAGE};
  private static final PsiAnnotation.TargetType[] TYPE_USE_TARGETS = {PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] ANNOTATION_TARGETS = {PsiAnnotation.TargetType.ANNOTATION_TYPE, PsiAnnotation.TargetType.TYPE, PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] TYPE_TARGETS = {PsiAnnotation.TargetType.TYPE, PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] TYPE_PARAMETER_TARGETS = {
    PsiAnnotation.TargetType.TYPE_PARAMETER, PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] CONSTRUCTOR_TARGETS = {PsiAnnotation.TargetType.CONSTRUCTOR, PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] METHOD_TARGETS = {PsiAnnotation.TargetType.METHOD, PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] FIELD_TARGETS = {PsiAnnotation.TargetType.FIELD, PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] PARAMETER_TARGETS = {PsiAnnotation.TargetType.PARAMETER, PsiAnnotation.TargetType.TYPE_USE};
  private static final PsiAnnotation.TargetType[] LOCAL_VARIABLE_TARGETS ={
    PsiAnnotation.TargetType.LOCAL_VARIABLE, PsiAnnotation.TargetType.TYPE_USE};

  @NotNull
  public static PsiAnnotation.TargetType[] getTargetsForLocation(@Nullable PsiAnnotationOwner owner) {
    if (owner == null) {
      return PsiAnnotation.TargetType.EMPTY_ARRAY;
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
        return PARAMETER_TARGETS;
      }
      if (element instanceof PsiLocalVariable) {
        return LOCAL_VARIABLE_TARGETS;
      }
    }

    return PsiAnnotation.TargetType.EMPTY_ARRAY;
  }

  @Nullable
  public static Set<PsiAnnotation.TargetType> extractRequiredAnnotationTargets(@Nullable PsiAnnotationMemberValue value) {
    if (value instanceof PsiReference) {
      PsiAnnotation.TargetType targetType = translateTargetRef((PsiReference)value);
      if (targetType != null) {
        return Collections.singleton(targetType);
      }
    }
    else if (value instanceof PsiArrayInitializerMemberValue) {
      Set <PsiAnnotation.TargetType> targets = ContainerUtil.newHashSet();
      for (PsiAnnotationMemberValue initializer : ((PsiArrayInitializerMemberValue)value).getInitializers()) {
        if (initializer instanceof PsiReference) {
          PsiAnnotation.TargetType targetType = translateTargetRef((PsiReference)initializer);
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
  private static PsiAnnotation.TargetType translateTargetRef(@NotNull PsiReference reference) {
    PsiElement field = reference.resolve();
    if (field instanceof PsiEnumConstant) {
      String name = ((PsiEnumConstant)field).getName();
      try {
        return PsiAnnotation.TargetType.valueOf(name);
      }
      catch (IllegalArgumentException e) {
        LOG.warn("Unknown target: " + name);
      }
    }
    return null;
  }
}
