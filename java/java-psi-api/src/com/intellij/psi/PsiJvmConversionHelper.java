// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.psi.PsiType.getJavaLangObject;
import static com.intellij.psi.PsiType.getTypeByName;

class PsiJvmConversionHelper {

  private static final Logger LOG = Logger.getInstance(PsiJvmConversionHelper.class);
  private static final Map<String, JvmModifier> MODIFIERS = ContainerUtil.<String, JvmModifier>immutableMapBuilder()
    .put(PsiModifier.PUBLIC, JvmModifier.PUBLIC)
    .put(PsiModifier.PROTECTED, JvmModifier.PROTECTED)
    .put(PsiModifier.PRIVATE, JvmModifier.PRIVATE)
    .put(PsiModifier.PACKAGE_LOCAL, JvmModifier.PACKAGE_LOCAL)
    .put(PsiModifier.STATIC, JvmModifier.STATIC)
    .put(PsiModifier.ABSTRACT, JvmModifier.ABSTRACT)
    .put(PsiModifier.FINAL, JvmModifier.FINAL)
    .put(PsiModifier.NATIVE, JvmModifier.NATIVE)
    .put(PsiModifier.SYNCHRONIZED, JvmModifier.SYNCHRONIZED)
    .put(PsiModifier.STRICTFP, JvmModifier.STRICTFP)
    .put(PsiModifier.TRANSIENT, JvmModifier.TRANSIENT)
    .put(PsiModifier.VOLATILE, JvmModifier.VOLATILE)
    .put(PsiModifier.TRANSITIVE, JvmModifier.TRANSITIVE)
    .build();

  @NotNull
  static PsiAnnotation[] getListAnnotations(@NotNull PsiModifierListOwner modifierListOwner) {
    PsiModifierList list = modifierListOwner.getModifierList();
    return list == null ? PsiAnnotation.EMPTY_ARRAY : list.getAnnotations();
  }

  @NotNull
  static JvmModifier[] getListModifiers(@NotNull PsiModifierListOwner modifierListOwner) {
    final Set<JvmModifier> result = EnumSet.noneOf(JvmModifier.class);
    MODIFIERS.forEach((psi, jvm) -> {
      if (modifierListOwner.hasModifierProperty(psi)) {
        result.add(jvm);
      }
    });
    return result.toArray(JvmModifier.EMPTY_ARRAY);
  }

  @NotNull
  static JvmClassKind getJvmClassKind(@NotNull PsiClass psiClass) {
    if (psiClass.isAnnotationType()) return JvmClassKind.ANNOTATION;
    if (psiClass.isInterface()) return JvmClassKind.INTERFACE;
    if (psiClass.isEnum()) return JvmClassKind.ENUM;
    return JvmClassKind.CLASS;
  }

  @Nullable
  static JvmReferenceType getClassSuperType(@NotNull PsiClass psiClass) {
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
  static JvmReferenceType[] getClassInterfaces(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass != null && baseClass.isInterface()) {
        return new JvmReferenceType[]{baseClassType};
      }
      else {
        return JvmReferenceType.EMPTY_ARRAY;
      }
    }

    PsiReferenceList referenceList = psiClass.isInterface() ? psiClass.getExtendsList() : psiClass.getImplementsList();
    if (referenceList == null) return JvmReferenceType.EMPTY_ARRAY;
    return referenceList.getReferencedTypes();
  }
}
