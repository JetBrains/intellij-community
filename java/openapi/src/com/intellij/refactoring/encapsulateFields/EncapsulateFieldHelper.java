/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.encapsulateFields;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public abstract class EncapsulateFieldHelper {
  private static class Extension extends LanguageExtension<EncapsulateFieldHelper> {

    public Extension() {
      super("com.intellij.encapsulateFields.Helper");
    }
  }
  private static final Extension INSTANCE = new Extension();

  @NotNull
  public abstract PsiField[] getApplicableFields(@NotNull PsiClass aClass);

  @NotNull
  public abstract String suggestSetterName(@NotNull PsiField field);

  @NotNull
  public abstract String suggestGetterName(@NotNull PsiField field);

  @Nullable
  public abstract PsiMethod generateMethodPrototype(@NotNull PsiField field, @NotNull String methodName, boolean isGetter);

  public abstract boolean processUsage(@NotNull EncapsulateFieldUsageInfo usage,
                                       @NotNull EncapsulateFieldsDescriptor descriptor,
                                       PsiMethod setter,
                                       PsiMethod getter);

  @Nullable
  public abstract EncapsulateFieldUsageInfo createUsage(@NotNull EncapsulateFieldsDescriptor descriptor,
                                                        @NotNull FieldDescriptor fieldDescriptor,
                                                        @NotNull PsiReference reference);

  public static EncapsulateFieldHelper getHelper(@NotNull Language lang) {
    return INSTANCE.forLanguage(lang);
  }
}
