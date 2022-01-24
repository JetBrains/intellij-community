// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.rules.DisjunctionTypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.RootTypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author db
 */
public class TypeMigrationRules {
  private final List<TypeConversionRule> myConversionRules;
  private final Map<Class, Object> myConversionCustomSettings = new HashMap<>();
  private final Project myProject;
  private SearchScope mySearchScope;

  public TypeMigrationRules(@NotNull Project project) {
    myProject = project;
    final List<TypeConversionRule> extensions = TypeConversionRule.EP_NAME.getExtensionList();
    myConversionRules = new ArrayList<>(extensions.size() + 2);
    myConversionRules.add(new RootTypeConversionRule());
    myConversionRules.add(new DisjunctionTypeConversionRule());
    myConversionRules.addAll(extensions);
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

  public void setBoundScope(@NotNull SearchScope searchScope) {
    mySearchScope = searchScope.intersectWith(GlobalSearchScope.notScope(LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope()));
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
