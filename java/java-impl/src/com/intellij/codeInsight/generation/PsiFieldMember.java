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
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class PsiFieldMember extends PsiElementClassMember<PsiField> implements PropertyClassMember {
  private static final int FIELD_OPTIONS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER;

  public PsiFieldMember(final PsiField field) {
    super(field, PsiFormatUtil.formatVariable(field, FIELD_OPTIONS, PsiSubstitutor.EMPTY));
  }

  public PsiFieldMember(PsiField psiMember, PsiSubstitutor substitutor) {
    super(psiMember, substitutor, PsiFormatUtil.formatVariable(psiMember, FIELD_OPTIONS, PsiSubstitutor.EMPTY));
  }

  @Nullable
  @Override
  public GenerationInfo generateGetter() throws IncorrectOperationException {
    final GenerationInfo[] infos = generateGetters();
    return infos != null && infos.length > 0 ? infos[0] : null;
  }

  @Nullable
  @Override
  public GenerationInfo[] generateGetters() throws IncorrectOperationException {
    final PsiField field = getElement();
    return createGenerateInfos(field, GetterSetterPrototypeProvider.generateGetterSetters(field, true));
  }

  @Nullable
  @Override
  public GenerationInfo generateSetter() throws IncorrectOperationException {
    final GenerationInfo[] infos = generateSetters();
    return infos != null && infos.length > 0 ? infos[0] : null;
  }

  @Override
  @Nullable
  public GenerationInfo[] generateSetters() {
    final PsiField field = getElement();
    if (GetterSetterPrototypeProvider.isReadOnlyProperty(field)) {
      return null;
    }
    return createGenerateInfos(field, GetterSetterPrototypeProvider.generateGetterSetters(field, false));
  }

  private static GenerationInfo[] createGenerateInfos(PsiField field, PsiMethod[] prototypes) {
    final List<GenerationInfo> methods = new ArrayList<GenerationInfo>();
    for (PsiMethod prototype : prototypes) {
      final PsiMethod method = createMethodIfNotExists(field, prototype);
      if (method != null) {
        methods.add(new PsiGenerationInfo(method));
      }
    }
    return methods.isEmpty() ? null : methods.toArray(new GenerationInfo[methods.size()]);
  }

  @Nullable
  private static PsiMethod createMethodIfNotExists(final PsiField field, final PsiMethod template) {
    final PsiClass aClass = field.getContainingClass();
    PsiMethod existing = aClass.findMethodBySignature(template, false);
    if (existing == null) {
      if (template != null) {
        String modifier = aClass.isEnum() && aClass.hasModifierProperty(PsiModifier.PUBLIC) ? null : PsiUtil.getMaximumModifierForMember(aClass);
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
}
