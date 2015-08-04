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
}
