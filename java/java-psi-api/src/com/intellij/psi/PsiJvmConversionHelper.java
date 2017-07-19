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

import com.intellij.lang.jvm.*;
import com.intellij.lang.jvm.types.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

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
  public static JvmClassType getClassSuperType(@NotNull PsiClass psiClass) {
    // TODO
    throw new RuntimeException("not implemented");
  }

  @NotNull
  public static Iterable<JvmClassType> getClassInterfaces(@NotNull PsiClass psiClass) {
    // TODO
    throw new RuntimeException("not implemented");
  }

  @NotNull
  public static JvmType getMethodReturnType(@NotNull PsiMethod method) {
    LOG.assertTrue(!method.isConstructor());
    final PsiType type = method.getReturnType();
    LOG.assertTrue(type != null);
    return toJvmType(type);
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
    return ContainerUtil.map(referenceList.getReferencedTypes(), it -> toJvmReferenceType(it));
  }

  @NotNull
  public static JvmType toJvmType(@NotNull PsiType type) {
    if (type instanceof PsiPrimitiveType || type instanceof PsiWildcardType || type instanceof PsiArrayType) {
      return ((JvmType)type);
    }
    else if (type instanceof PsiClassType) {
      return toJvmReferenceType(((PsiClassType)type));
    }
    throw new IllegalArgumentException("Unsupported type: " + type);
  }

  @NotNull
  public static JvmReferenceType toJvmReferenceType(@NotNull PsiClassType type) {
    return type.hasParameters() ? toJvmClassType(type) : type;
  }

  @NotNull
  public static JvmClassType toJvmClassType(@NotNull PsiClassType type) {
    return new PsiJvmClassType(type);
  }

  private static class PsiJvmClassType implements JvmClassType {

    private final @NotNull PsiClassType myPsiClassType;

    private PsiJvmClassType(@NotNull PsiClassType type) {
      myPsiClassType = type;
    }

    @NotNull
    @Override
    public String getName() {
      return myPsiClassType.getClassName();
    }

    @NotNull
    @Override
    public JvmAnnotation[] getAnnotations() {
      return myPsiClassType.getAnnotations();
    }

    @Nullable
    @Override
    public JvmGenericResolveResult resolveType() {
      final ClassResolveResult classResolveResult = myPsiClassType.resolveGenerics();
      final PsiClass clazz = classResolveResult.getElement();
      if (clazz == null || clazz instanceof PsiTypeParameter) return null;

      final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
      return new JvmGenericResolveResult() {

        private final JvmSubstitutor mySubstitutor = new PsiJvmSubstitutor(substitutor);

        @NotNull
        @Override
        public JvmClass getDeclaration() {
          return clazz;
        }

        @NotNull
        @Override
        public JvmSubstitutor getSubstitutor() {
          return mySubstitutor;
        }
      };
    }

    @NotNull
    @Override
    public Iterable<JvmType> typeArguments() {
      return ContainerUtil.map(myPsiClassType.getParameters(), it -> toJvmType(it));
    }
  }

  private static class PsiJvmSubstitutor implements JvmSubstitutor {

    private final @NotNull PsiSubstitutor mySubstitutor;

    private PsiJvmSubstitutor(@NotNull PsiSubstitutor substitutor) {
      mySubstitutor = substitutor;
    }

    @Nullable
    @Override
    public JvmType substitute(@NotNull JvmTypeParameter typeParameter) {
      if (!(typeParameter instanceof PsiTypeParameter)) return null;
      PsiTypeParameter psiTypeParameter = ((PsiTypeParameter)typeParameter);
      PsiType substituted = mySubstitutor.substitute(psiTypeParameter);
      return substituted == null ? null : toJvmType(substituted);
    }
  }
}
