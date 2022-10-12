// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiFieldMember extends PsiElementClassMember<PsiField> implements PropertyClassMember {
  private static final int FIELD_OPTIONS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER;

  public PsiFieldMember(@NotNull PsiField field) {
    super(field, PsiFormatUtil.formatVariable(field, FIELD_OPTIONS, PsiSubstitutor.EMPTY));
  }

  public PsiFieldMember(@NotNull PsiField psiMember, PsiSubstitutor substitutor) {
    super(psiMember, substitutor, PsiFormatUtil.formatVariable(psiMember, FIELD_OPTIONS, PsiSubstitutor.EMPTY));
  }

  @Nullable
  @Override
  public GenerationInfo generateGetter() throws IncorrectOperationException {
    final GenerationInfo[] infos = generateGetters(getElement().getContainingClass());
    return infos != null && infos.length > 0 ? infos[0] : null;
  }

  @Override
  public GenerationInfo @Nullable [] generateGetters(PsiClass aClass) throws IncorrectOperationException {
    PsiField field = getElement();
    if (field.hasModifierProperty(PsiModifier.STATIC) && 
        field.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }
    return createGenerateInfos(aClass, GetterSetterPrototypeProvider.generateGetterSetters(field, true, false));
  }

  @Nullable
  @Override
  public GenerationInfo generateSetter() throws IncorrectOperationException {
    final GenerationInfo[] infos = generateSetters(getElement().getContainingClass());
    return infos != null && infos.length > 0 ? infos[0] : null;
  }

  @Override
  public boolean isReadOnlyMember() {
    return GetterSetterPrototypeProvider.isReadOnlyProperty(getElement());
  }

  @Override
  public GenerationInfo @Nullable [] generateSetters(PsiClass aClass) {
    final PsiField field = getElement();
    if (GetterSetterPrototypeProvider.isReadOnlyProperty(field)) {
      return null;
    }
    return createGenerateInfos(aClass, GetterSetterPrototypeProvider.generateGetterSetters(field, false, false));
  }

  private static GenerationInfo[] createGenerateInfos(PsiClass aClass, PsiMethod[] prototypes) {
    final List<GenerationInfo> methods = new ArrayList<>();
    for (PsiMethod prototype : prototypes) {
      final PsiMethod method = createMethodIfNotExists(aClass, prototype);
      if (method != null) {
        methods.add(new PsiGenerationInfo(method));
      }
    }
    return methods.isEmpty() ? null : methods.toArray(GenerationInfo.EMPTY_ARRAY);
  }

  @Nullable
  private static PsiMethod createMethodIfNotExists(PsiClass aClass, final PsiMethod template) {
    PsiMethod existing = aClass.findMethodBySignature(template, false);
    return existing == null || !existing.isPhysical() ? template : null;
  }
}
