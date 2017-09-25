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
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.rules.DisjunctionTypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.RootTypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author db
 * Date: Oct 2, 2004
 */
public class TypeMigrationRules {
  private final List<TypeConversionRule> myConversionRules;
  private final Map<Class, Object> myConversionCustomSettings = new HashMap<>();
  private SearchScope mySearchScope;

  public TypeMigrationRules() {
    final TypeConversionRule[] extensions = Extensions.getExtensions(TypeConversionRule.EP_NAME);
    myConversionRules = new ArrayList<>(extensions.length + 2);
    myConversionRules.add(new RootTypeConversionRule());
    myConversionRules.add(new DisjunctionTypeConversionRule());
    ContainerUtil.addAll(myConversionRules, extensions);
    addConversionRuleSettings(new MigrateGetterNameSetting());
  }

  public void addConversionDescriptor(TypeConversionRule rule) {
    myConversionRules.add(rule);
  }

  public void addConversionRuleSettings(Object settings) {
    myConversionCustomSettings.put(settings.getClass(), settings);
  }

  public <T> T getConversionSettings(Class<T> aClass) {
    return (T)myConversionCustomSettings.get(aClass);
  }

  @NonNls
  @Nullable
  public TypeConversionDescriptorBase findConversion(final PsiType from, final PsiType to, final PsiMember member, final PsiExpression context,
                                                     final boolean isCovariantPosition, final TypeMigrationLabeler labeler) {
    final TypeConversionDescriptorBase conversion = findConversion(from, to, member, context, labeler);
    if (conversion != null) return conversion;

    if (isCovariantPosition) {
      if (to instanceof PsiEllipsisType) {
        if (TypeConversionUtil.isAssignable(((PsiEllipsisType)to).getComponentType(), from)) return new TypeConversionDescriptorBase();
      }
      if (TypeConversionUtil.isAssignable(to, from)) return new TypeConversionDescriptorBase();
    }

    return !isCovariantPosition && TypeConversionUtil.isAssignable(from, to) ? new TypeConversionDescriptorBase() : null;
  }

  @Nullable
  public TypeConversionDescriptorBase findConversion(final PsiType from, final PsiType to, final PsiMember member,
                                                     final PsiExpression context, final TypeMigrationLabeler labeler) {
    for (TypeConversionRule descriptor : myConversionRules) {
      final TypeConversionDescriptorBase conversion = descriptor.findConversion(from, to, member, context, labeler);
      if (conversion != null) return conversion;
    }
    return null;
  }

  public boolean shouldConvertNull(final PsiType from, final PsiType to, PsiExpression context) {
    return myConversionRules.stream().anyMatch(rule -> rule.shouldConvertNullInitializer(from, to, context));
  }

  public void setBoundScope(final SearchScope searchScope) {
    mySearchScope = searchScope;
  }

  public SearchScope getSearchScope() {
    return mySearchScope;
  }

  @Nullable
  public Pair<PsiType, PsiType> bindTypeParameters(final PsiType from, final PsiType to, final PsiMethod method,
                                                   final PsiExpression context, final TypeMigrationLabeler labeler) {
    for (TypeConversionRule conversionRule : myConversionRules) {
      final Pair<PsiType, PsiType> typePair = conversionRule.bindTypeParameters(from, to, method, context, labeler);
      if (typePair != null) return typePair;
    }
    return null;
  }
}
