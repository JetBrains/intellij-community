/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.refactoring.extractclass;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;

import java.util.List;

public class EnumTypeConversionRule extends TypeConversionRule {
  private final List<PsiField> myEnumConstants;

  public EnumTypeConversionRule(List<PsiField> enumConstants) {
    myEnumConstants = enumConstants;
  }

  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
                                                     PsiType to,
                                                     PsiMember member,
                                                     PsiExpression context,
                                                     TypeMigrationLabeler labeler) {
    final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(context, PsiMethodCallExpression.class, false);
    if (callExpression != null) {
      final PsiMethod resolved = callExpression.resolveMethod();
      if (resolved != null) {
        final SearchScope searchScope = labeler.getRules().getSearchScope();
        if (!PsiSearchScopeUtil.isInScope(searchScope, resolved)) {
          return null;
        }
      }
    }
    final PsiField field = PsiTreeUtil.getParentOfType(context, PsiField.class);
    if (field != null &&
        !myEnumConstants.contains(field) &&
        field.hasModifierProperty(PsiModifier.STATIC) &&
        field.hasModifierProperty(PsiModifier.FINAL) &&
        field.hasInitializer()) {
      return null;
    }
    final PsiClass toClass = PsiUtil.resolveClassInType(to);
    if (toClass != null && toClass.isEnum()) {
      final PsiMethod[] constructors = toClass.getConstructors();
      if (constructors.length == 1) {
        final PsiMethod constructor = constructors[0];
        final PsiParameter[] parameters = constructor.getParameterList().getParameters();
        if (parameters.length == 1) {
          if (TypeConversionUtil.isAssignable(parameters[0].getType(), from)) {
            return new TypeConversionDescriptorBase();
          }
        }
      }
    }
    return null;
  }
}