// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.Contract;

public abstract class GetterSetterPrototypeProvider {
  public static final ExtensionPointName<GetterSetterPrototypeProvider> EP_NAME = ExtensionPointName.create("com.intellij.getterSetterProvider");
  @Contract(pure = true)
  public abstract boolean canGeneratePrototypeFor(PsiField field);
  public abstract PsiMethod[] generateGetters(PsiField field);
  public abstract PsiMethod[] generateSetters(PsiField field);
  @Contract(pure = true)
  public PsiMethod[] findGetters(PsiClass psiClass, String propertyName) {
    return null;
  }

  @Contract(pure = true)
  public String suggestGetterName(String propertyName) {
    return null;
  }

  @Contract(pure = true)
  public boolean isSimpleGetter(PsiMethod method, String oldPropertyName) {
    return false;
  }

  @Contract(pure = true)
  public abstract boolean isReadOnly(PsiField field);

  public static PsiMethod[] generateGetterSetters(PsiField field, boolean generateGetter) {
    return generateGetterSetters(field, generateGetter, true);
  }

  public static PsiMethod[] generateGetterSetters(PsiField field,
                                                  boolean generateGetter,
                                                  boolean ignoreInvalidTemplate) {
    for (GetterSetterPrototypeProvider provider : EP_NAME.getExtensionList()) {
      if (provider.canGeneratePrototypeFor(field)) {
        return generateGetter ? provider.generateGetters(field) : provider.generateSetters(field);
      }
    }
    return new PsiMethod[]{generateGetter ? GenerateMembersUtil.generateGetterPrototype(field, ignoreInvalidTemplate) :
                           GenerateMembersUtil.generateSetterPrototype(field, ignoreInvalidTemplate)};
  }

  public static boolean isReadOnlyProperty(PsiField field) {
    for (GetterSetterPrototypeProvider provider : EP_NAME.getExtensionList()) {
      if (DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(
        () -> provider.canGeneratePrototypeFor(field) && provider.isReadOnly(field))) {
        return true;
      }
    }
    return field.hasModifierProperty(PsiModifier.FINAL);
  }

  public static PsiMethod[] findGetters(PsiClass aClass, String propertyName, boolean isStatic) {
    if (!isStatic) {
      for (GetterSetterPrototypeProvider provider : EP_NAME.getExtensionList()) {
        final PsiMethod[] getterSetter = provider.findGetters(aClass, propertyName);
        if (getterSetter != null) return getterSetter;
      }
    }
    final PsiMethod propertyGetterSetter = PropertyUtilBase.findPropertyGetter(aClass, propertyName, isStatic, false);
    if (propertyGetterSetter != null) {
      return new PsiMethod[] {propertyGetterSetter};
    }
    return null;
  }

  public static String suggestNewGetterName(String oldPropertyName, String newPropertyName, PsiMethod method) {
    for (GetterSetterPrototypeProvider provider : EP_NAME.getExtensionList()) {
      if (provider.isSimpleGetter(method, oldPropertyName)) {
        return provider.suggestGetterName(newPropertyName);
      }
    }
    return null;
  }
}
