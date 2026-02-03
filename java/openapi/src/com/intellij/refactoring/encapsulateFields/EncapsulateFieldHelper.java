// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * Pluggable part of Encapsulate Field java refactoring
 * 
 * Allows declaring which fields are subjects to be encapsulated, 
 * what accessors names should be generated and corresponding method prototypes.
 * 
 * With {@link #createUsage(EncapsulateFieldsDescriptor, FieldDescriptor, PsiReference)} and 
 * {@link #processUsage(EncapsulateFieldUsageInfo, EncapsulateFieldsDescriptor, PsiMethod, PsiMethod)}
 * extension may define how field usages should be replaced with generated accessors
 */
public abstract class EncapsulateFieldHelper {
  private static class Extension extends LanguageExtension<EncapsulateFieldHelper> {

    Extension() {
      super("com.intellij.encapsulateFields.Helper");
    }
  }
  private static final Extension INSTANCE = new Extension();

  public abstract PsiField @NotNull [] getApplicableFields(@NotNull PsiClass aClass);

  public abstract @NotNull String suggestSetterName(@NotNull PsiField field);

  public abstract @NotNull String suggestGetterName(@NotNull PsiField field);

  public abstract @Nullable PsiMethod generateMethodPrototype(@NotNull PsiField field, @NotNull String methodName, boolean isGetter);

  public abstract boolean processUsage(@NotNull EncapsulateFieldUsageInfo usage,
                                       @NotNull EncapsulateFieldsDescriptor descriptor,
                                       PsiMethod setter,
                                       PsiMethod getter);

  public abstract @Nullable EncapsulateFieldUsageInfo createUsage(@NotNull EncapsulateFieldsDescriptor descriptor,
                                                                  @NotNull FieldDescriptor fieldDescriptor,
                                                                  @NotNull PsiReference reference);

  public static EncapsulateFieldHelper getHelper(@NotNull Language lang) {
    return INSTANCE.forLanguage(lang);
  }
}
