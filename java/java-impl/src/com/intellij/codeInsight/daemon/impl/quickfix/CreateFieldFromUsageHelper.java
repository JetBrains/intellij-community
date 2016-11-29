/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.Template;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public abstract class CreateFieldFromUsageHelper {
  private static final LanguageExtension<CreateFieldFromUsageHelper> EP_NAME =
    new LanguageExtension<>("com.intellij.codeInsight.createFieldFromUsageHelper");

  public static Template setupTemplate(PsiField field,
                                       Object expectedTypes,
                                       PsiClass targetClass,
                                       Editor editor,
                                       PsiElement context, boolean createConstantField) {
    CreateFieldFromUsageHelper helper = EP_NAME.forLanguage(field.getLanguage());
    if (helper == null) return null;
    return helper.setupTemplateImpl(field, expectedTypes, targetClass, editor, context, createConstantField,
                                    CreateFromUsageBaseFix.getTargetSubstitutor(context));
  }

  public static PsiField insertField(@NotNull PsiClass targetClass, @NotNull PsiField field, @NotNull PsiElement place) {
    CreateFieldFromUsageHelper helper = EP_NAME.forLanguage(field.getLanguage());
    if (helper == null) return null;
    return helper.insertFieldImpl(targetClass, field, place);
  }

  public abstract PsiField insertFieldImpl(@NotNull PsiClass targetClass, @NotNull PsiField field, @NotNull PsiElement place);

  public abstract Template setupTemplateImpl(PsiField field,
                                             Object expectedTypes,
                                             PsiClass targetClass,
                                             Editor editor,
                                             PsiElement context,
                                             boolean createConstantField, PsiSubstitutor substitutor);
}
