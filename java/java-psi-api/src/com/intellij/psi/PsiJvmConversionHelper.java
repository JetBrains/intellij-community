// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.lang.jvm.JvmEnumField;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static com.intellij.psi.PsiType.getJavaLangObject;
import static com.intellij.psi.PsiType.getTypeByName;

class PsiJvmConversionHelper {

  private static final Logger LOG = Logger.getInstance(PsiJvmConversionHelper.class);
  private static final Map<JvmModifier, String> MODIFIERS;

  static {
    Map<JvmModifier, String> modifiers = new EnumMap<>(JvmModifier.class);
    modifiers.put(JvmModifier.PUBLIC, PsiModifier.PUBLIC);
    modifiers.put(JvmModifier.PROTECTED, PsiModifier.PROTECTED);
    modifiers.put(JvmModifier.PRIVATE, PsiModifier.PRIVATE);
    modifiers.put(JvmModifier.PACKAGE_LOCAL, PsiModifier.PACKAGE_LOCAL);
    modifiers.put(JvmModifier.STATIC, PsiModifier.STATIC);
    modifiers.put(JvmModifier.ABSTRACT, PsiModifier.ABSTRACT);
    modifiers.put(JvmModifier.FINAL, PsiModifier.FINAL);
    modifiers.put(JvmModifier.NATIVE, PsiModifier.NATIVE);
    modifiers.put(JvmModifier.SYNCHRONIZED, PsiModifier.SYNCHRONIZED);
    modifiers.put(JvmModifier.STRICTFP, PsiModifier.STRICTFP);
    modifiers.put(JvmModifier.TRANSIENT, PsiModifier.TRANSIENT);
    modifiers.put(JvmModifier.VOLATILE, PsiModifier.VOLATILE);
    modifiers.put(JvmModifier.TRANSITIVE, PsiModifier.TRANSITIVE);
    MODIFIERS = Collections.unmodifiableMap(modifiers);
  }

  @NotNull
  static PsiAnnotation[] getListAnnotations(@NotNull PsiModifierListOwner modifierListOwner) {
    PsiModifierList list = modifierListOwner.getModifierList();
    return list == null ? PsiAnnotation.EMPTY_ARRAY : list.getAnnotations();
  }

  @Nullable
  static PsiAnnotation getListAnnotation(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String fqn) {
    PsiModifierList list = modifierListOwner.getModifierList();
    return list == null ? null : list.findAnnotation(fqn);
  }

  static boolean hasListAnnotation(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String fqn) {
    PsiModifierList list = modifierListOwner.getModifierList();
    return list != null && list.hasAnnotation(fqn);
  }

  static boolean hasListModifier(@NotNull PsiModifierListOwner modifierListOwner, @NotNull JvmModifier modifier) {
    return modifierListOwner.hasModifierProperty(MODIFIERS.get(modifier));
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

  @NotNull
  static String getAnnotationAttributeName(@NotNull PsiNameValuePair pair) {
    String name = pair.getName();
    return name == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : name;
  }

  @Nullable
  static JvmAnnotationAttributeValue getAnnotationAttributeValue(@NotNull PsiNameValuePair pair) {
    return getAnnotationAttributeValue(pair.getValue());
  }

  @Nullable
  static JvmAnnotationAttributeValue getAnnotationAttributeValue(@Nullable PsiAnnotationMemberValue value) {
    if (value instanceof PsiClassObjectAccessExpression) {
      return new PsiAnnotationClassValue((PsiClassObjectAccessExpression)value);
    }
    if (value instanceof PsiAnnotation) {
      return new PsiNestedAnnotationValue((PsiAnnotation)value);
    }
    if (value instanceof PsiArrayInitializerMemberValue) {
      return new PsiAnnotationArrayValue((PsiArrayInitializerMemberValue)value);
    }
    if (value instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)value).resolve();
      if (resolved instanceof JvmEnumField) {
        return new PsiAnnotationEnumFieldValue((PsiReferenceExpression)value, (JvmEnumField)resolved);
      }
    }
    if (value instanceof PsiExpression) {
      return new PsiAnnotationConstantValue((PsiExpression)value);
    }

    if (value != null) {
      LOG.warn(new RuntimeExceptionWithAttachments("Not implemented: " + value.getClass(), new Attachment("text", value.getText())));
    }

    return null;
  }
}
