// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.*;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface JvmPsiConversionHelper {

  @NotNull
  static JvmPsiConversionHelper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JvmPsiConversionHelper.class);
  }

  //TODO: this is a hack, will be removed when a better way will be found
  @Deprecated
  @NotNull
  static Project getProject(@NotNull JvmElement jvmElement) {
    return ((PsiElement)jvmElement).getProject();
  }

  //TODO: this is a hack, will be removed when a better way will be found
  @Deprecated
  @NotNull
  static Project getProject(@NotNull JvmAnnotationTreeElement jvmElement) {
    if (jvmElement instanceof PsiElement) {
      return ((PsiElement)jvmElement).getProject();
    }
    if (jvmElement instanceof JvmElement) {
      return ((JvmElement)jvmElement).getSourceElement().getProject();
    }
    throw new UnsupportedOperationException("Not implemented for " + jvmElement);
  }

  @Nullable
  PsiClass convertTypeDeclaration(@Nullable JvmTypeDeclaration typeDeclaration);

  @NotNull
  PsiAnnotation convertAnnotation(@NotNull JvmAnnotation annotation);

  @NotNull
  PsiModifierListOwner convertModifierOwner(@NotNull JvmAnnotatedElement jvmModifiersOwner);

  @Nullable
  default PsiElement convertJvmTreeElement(@NotNull JvmAnnotationTreeElement jvmElement) {
    if (jvmElement instanceof JvmAnnotation) {
      return convertAnnotation(((JvmAnnotation)jvmElement));
    }
    if (jvmElement instanceof JvmAnnotatedElement) {
      return convertModifierOwner(((JvmAnnotatedElement)jvmElement));
    }
    throw new UnsupportedOperationException("not implemented for " + jvmElement);
  }

  @NotNull
  PsiTypeParameter convertTypeParameter(@NotNull JvmTypeParameter typeParameter);

  @NotNull
  PsiType convertType(@NotNull JvmType type);

  @NotNull
  PsiSubstitutor convertSubstitutor(@NotNull JvmSubstitutor substitutor);

  @Nullable
  static PsiElement convertATE(@NotNull JvmAnnotationTreeElement param) {
    return getInstance(getProject(param)).convertJvmTreeElement(param);
  }


  @Contract("null -> null; !null -> !null")
  static PsiAnnotation convertA(JvmAnnotation param) {
    if (param == null) return null;
    return getInstance(getProject((JvmAnnotationTreeElement)param)).convertAnnotation(param);
  }

  static PsiClass convertC(JvmClass param) {
    if (param == null) return null;
    return getInstance(getProject((JvmAnnotationTreeElement)param)).convertTypeDeclaration(param);
  }
}
