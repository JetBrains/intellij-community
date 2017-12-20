/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public abstract class TypeConversionRule {
  public static final ExtensionPointName<TypeConversionRule> EP_NAME = ExtensionPointName.create("com.intellij.conversion.rule");
  @Nullable
  public abstract TypeConversionDescriptorBase findConversion(final PsiType from,
                                                              final PsiType to,
                                                              final PsiMember member,
                                                              final PsiExpression context,
                                                              final TypeMigrationLabeler labeler);


  @Nullable
  public Pair<PsiType, PsiType> bindTypeParameters(PsiType from, PsiType to, final PsiMethod method, final PsiExpression context,
                                                   final TypeMigrationLabeler labeler) {
    return null;
  }

  public boolean shouldConvertNullInitializer(PsiType from, PsiType to, PsiExpression context) {
    return false;
  }
}