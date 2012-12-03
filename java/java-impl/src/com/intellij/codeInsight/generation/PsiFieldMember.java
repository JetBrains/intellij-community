/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiFieldMember extends PsiElementClassMember<PsiField> implements EncapsulatableClassMember {
  private static final int FIELD_OPTIONS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER;

  public PsiFieldMember(final PsiField field) {
    super(field, PsiFormatUtil.formatVariable(field, FIELD_OPTIONS, PsiSubstitutor.EMPTY));
  }

  public PsiFieldMember(PsiField psiMember, PsiSubstitutor substitutor) {
    super(psiMember, substitutor, PsiFormatUtil.formatVariable(psiMember, FIELD_OPTIONS, PsiSubstitutor.EMPTY));
  }

  @Override
  @Nullable
  public PsiGenerationInfo generateGetter() {
    PsiField field = getElement();
    final PsiMethod method = createMethodIfNotExists(field, PropertyUtil.generateGetterPrototype(field));
    return method != null ? new PsiGenerationInfo(method) : null;
  }

  @Nullable
  private static PsiMethod createMethodIfNotExists(final PsiField field, final PsiMethod template) {
    final PsiClass aClass = field.getContainingClass();
    PsiMethod existing = aClass.findMethodBySignature(template, false);
    if (existing == null) {
      if (template != null) {
        String modifier = PsiUtil.getMaximumModifierForMember(aClass);
        if (modifier != null) {
          PsiUtil.setModifierProperty(template, modifier, true);
        }
      }
      return template;
    }
    else {
      return null;
    }
  }

  @Override
  @Nullable
  public PsiGenerationInfo generateSetter() {
    PsiField field = getElement();
    if (field.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }
    final PsiMethod method = createMethodIfNotExists(field, PropertyUtil.generateSetterPrototype(field));
    return method == null ? null : new PsiGenerationInfo(method);
  }
}
