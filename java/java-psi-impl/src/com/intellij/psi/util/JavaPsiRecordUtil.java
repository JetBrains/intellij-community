// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods to support Java records
 */
public class JavaPsiRecordUtil {
  /**
   * @param accessor accessor method for record component 
   * @return a corresponding record component, or null if the supplied method is not an accessor for the record component.
   * Note that if accessor is not well-formed (e.g. has wrong return type), the corresponding record component will still be returned.
   */
  @Nullable
  public static PsiRecordComponent getRecordComponentForAccessor(@NotNull PsiMethod accessor) {
    PsiClass aClass = accessor.getContainingClass();
    if (aClass == null) {
      PsiElement parent = accessor.getParent();
      if (parent instanceof DummyHolder) {
        aClass = ObjectUtils.tryCast(parent.getContext(), PsiClass.class);
      }
    }
    if (aClass == null || !aClass.isRecord()) return null;
    if (!accessor.getParameterList().isEmpty()) return null;
    String name = accessor.getName();
    for (PsiRecordComponent c : aClass.getRecordComponents()) {
      if (name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  /**
   * @param component record component
   * @return synthetic field that corresponds to given component, or null if not found (e.g. if this component doesn't belong to a class) 
   */
  @Nullable
  public static PsiField getFieldForComponent(@NotNull PsiRecordComponent component) {
    PsiClass aClass = component.getContainingClass();
    if (aClass == null) return null;
    String name = component.getName();
    for (PsiField field : aClass.getFields()) {
      if (field.getName().equals(name) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        return field;
      }
    }
    return null;
  }

  /**
   * @param field synthetic field that corresponds to the record component
   * @return the corresponding record component; null if given field doesn't correspond to the record component.
   */
  @Nullable
  public static PsiRecordComponent getComponentForField(@NotNull PsiField field) {
    return field instanceof LightRecordField ? ((LightRecordField)field).getRecordComponent() : null;
  }

  /**
   * @param method method to check
   * @return true if given method is a compact constructor (has no parameter list),
   * regardless whether it's declared in the record or not
   */
  public static boolean isCompactConstructor(@NotNull PsiMethod method) {
    return method.isConstructor() && method.getParameterList().getText() == null;
  }

  /**
   * @param method method to check
   * @return true if given method is an explicit canonical (non-compact) constructor for a record class
   */
  public static boolean isExplicitCanonicalConstructor(@NotNull PsiMethod method) {
    if (!method.isConstructor() || isCompactConstructor(method)) return false;
    if (method instanceof SyntheticElement) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null || !aClass.isRecord()) return false;
    return hasCanonicalSignature(method, aClass.getRecordComponents());
  }

  private static boolean hasCanonicalSignature(@NotNull PsiMethod method, PsiRecordComponent[] components) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (components.length != parameters.length) return false;
    for (int i = 0; i < parameters.length; i++) {
      PsiType componentType = components[i].getType();
      PsiType parameterType = parameters[i].getType();
      if (componentType instanceof PsiEllipsisType) {
        componentType = ((PsiEllipsisType)componentType).toArrayType();
      }
      if (parameterType instanceof PsiEllipsisType) {
        parameterType = ((PsiEllipsisType)parameterType).toArrayType();
      }
      if (!TypeConversionUtil.erasure(componentType).equals(TypeConversionUtil.erasure(parameterType))) return false;
    }
    return true;
  }

  /**
   * @param recordClass record class
   * @return first explicitly declared canonical or compact constructor; 
   * null if the supplied class is not a record. Returns a synthetic constructor if it's not explicitly defined.
   */
  @Nullable
  public static PsiMethod findCanonicalConstructor(@NotNull PsiClass recordClass) {
    if (!recordClass.isRecord()) return null;
    PsiMethod[] constructors = recordClass.getConstructors();
    if (constructors.length == 0) return null;
    PsiRecordComponent[] components = recordClass.getRecordComponents();
    for (PsiMethod constructor : constructors) {
      if (isCompactConstructor(constructor) || hasCanonicalSignature(constructor, components)) {
        return constructor;
      }
    }
    return null;
  }
}
