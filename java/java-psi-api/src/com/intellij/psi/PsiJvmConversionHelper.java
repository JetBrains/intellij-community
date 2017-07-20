/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static com.intellij.psi.PsiType.getJavaLangObject;
import static com.intellij.psi.PsiType.getTypeByName;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class PsiJvmConversionHelper {

  private static final Logger LOG = Logger.getInstance(PsiJvmConversionHelper.class);

  @NotNull
  public static Iterable<JvmModifier> getModifiers(@NotNull PsiModifierListOwner modifierListOwner) {
    final Set<JvmModifier> result = EnumSet.noneOf(JvmModifier.class);
    for (@NonNls String modifier : PsiModifier.MODIFIERS) {
      if (modifierListOwner.hasModifierProperty(modifier)) {
        String jvmName = modifier.toUpperCase();
        JvmModifier jvmModifier = JvmModifier.valueOf(jvmName);
        result.add(jvmModifier);
      }
    }
    if (modifierListOwner.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      result.add(JvmModifier.PACKAGE_LOCAL);
    }
    return result;
  }

  @NotNull
  public static JvmClassKind getJvmClassKind(@NotNull PsiClass psiClass) {
    if (psiClass.isAnnotationType()) return JvmClassKind.ANNOTATION;
    if (psiClass.isInterface()) return JvmClassKind.INTERFACE;
    if (psiClass.isEnum()) return JvmClassKind.ENUM;
    return JvmClassKind.CLASS;
  }

  @Nullable
  public static JvmReferenceType getClassSuperType(@NotNull PsiClass psiClass) {
    if (psiClass.isInterface()) return null;
    if (psiClass.isEnum()) return getTypeByName(CommonClassNames.JAVA_LANG_ENUM, psiClass.getProject(), psiClass.getResolveScope());
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null || !baseClass.isInterface()) {
        return baseClassType;
      }
      else {
        return getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
      }
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return null;

    PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
    if (extendsTypes.length != 1) return getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
    return extendsTypes[0];
  }

  @NotNull
  public static Iterable<JvmReferenceType> getClassInterfaces(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass != null && baseClass.isInterface()) {
        return singletonList(baseClassType);
      }
      else {
        return emptyList();
      }
    }

    PsiReferenceList referenceList = psiClass.isInterface() ? psiClass.getExtendsList() : psiClass.getImplementsList();
    if (referenceList == null) return emptyList();
    return getReferencedTypes(referenceList);
  }

  @NotNull
  public static JvmType getMethodReturnType(@NotNull PsiMethod method) {
    LOG.assertTrue(!method.isConstructor());
    final PsiType type = method.getReturnType();
    LOG.assertTrue(type != null);
    return type;
  }

  @NotNull
  public static Iterable<JvmParameter> getMethodParameters(@NotNull PsiMethod method) {
    final PsiParameterList parameterList = method.getParameterList();
    return Arrays.asList(parameterList.getParameters());
  }

  @NotNull
  public static Iterable<JvmReferenceType> getMethodThrowsTypes(@NotNull PsiMethod method) {
    return getReferencedTypes(method.getThrowsList());
  }

  @NotNull
  public static Iterable<JvmReferenceType> getTypeParameterBounds(@NotNull PsiTypeParameter typeParameter) {
    return getReferencedTypes(typeParameter.getExtendsList());
  }

  private static Iterable<JvmReferenceType> getReferencedTypes(@NotNull PsiReferenceList referenceList) {
    return ContainerUtil.map(referenceList.getReferencedTypes(), it -> it);
  }

  static class PsiJvmSubstitutor implements JvmSubstitutor {

    private final @NotNull PsiSubstitutor mySubstitutor;

    PsiJvmSubstitutor(@NotNull PsiSubstitutor substitutor) {
      mySubstitutor = substitutor;
    }

    @Nullable
    @Override
    public JvmType substitute(@NotNull JvmTypeParameter typeParameter) {
      if (!(typeParameter instanceof PsiTypeParameter)) return null;
      PsiTypeParameter psiTypeParameter = ((PsiTypeParameter)typeParameter);
      return mySubstitutor.substitute(psiTypeParameter);
    }
  }
}
