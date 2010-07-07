package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.rules.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Oct 2, 2004
 * Time: 9:24:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class TypeMigrationRules {
  private final LinkedList<TypeConversionRule> myConversionRules = new LinkedList<TypeConversionRule>();

  private final PsiType myRootType;
  private PsiType myMigrationRootType;
  private SearchScope mySearchScope;

  public TypeMigrationRules(final PsiType root) {
    myRootType = root;
    myConversionRules.add(new RootTypeConversionRule());
    ContainerUtil.addAll(myConversionRules, Extensions.getExtensions(TypeConversionRule.EP_NAME));
  }

  public void setMigrationRootType(PsiType migrationRootType) {
    myMigrationRootType = migrationRootType;
  }

  public PsiType getRootType() {
    return myRootType;
  }

  public PsiType getMigrationRootType() {
    return myMigrationRootType;
  }

  public void addConversionDescriptor(TypeConversionRule rule) {
    myConversionRules.add(rule);
  }

  @NonNls
  @Nullable
  public TypeConversionDescriptorBase findConversion(final PsiType from, final PsiType to, PsiMember member, final PsiExpression context, final boolean isCovariantPosition,
                                                     final TypeMigrationLabeler labeler) {
    final TypeConversionDescriptorBase conversion = findConversion(from, to, member, context, labeler);
    if (conversion != null) return conversion;

    if (isCovariantPosition) {
      if (to instanceof PsiEllipsisType) {
        if (TypeConversionUtil.isAssignable(((PsiEllipsisType)to).getComponentType(), from)) return new TypeConversionDescriptorBase();
      }
      if (TypeConversionUtil.isAssignable(to, from)) return new TypeConversionDescriptorBase();
    }
    if (!isCovariantPosition && TypeConversionUtil.isAssignable(from, to)) return new TypeConversionDescriptorBase();
    return null;
  }

  @Nullable
  public TypeConversionDescriptorBase findConversion(PsiType from, PsiType to, PsiMember member, PsiExpression context, TypeMigrationLabeler labeler) {
    for (TypeConversionRule descriptor : myConversionRules) {
      final TypeConversionDescriptorBase conversion = descriptor.findConversion(from, to, member, context, labeler);
      if (conversion != null) return conversion;
    }
    return null;
  }

  public void setBoundScope(final SearchScope searchScope) {
    mySearchScope = searchScope;
  }

  public SearchScope getSearchScope() {
    return mySearchScope;
  }

  @Nullable
  public Pair<PsiType, PsiType> bindTypeParameters(final PsiType from, final PsiType to, final PsiMethod method, final PsiExpression context, final TypeMigrationLabeler labeler) {
    for (TypeConversionRule conversionRule : myConversionRules) {
      final Pair<PsiType, PsiType> typePair = conversionRule.bindTypeParameters(from, to, method, context, labeler);
      if (typePair != null) return typePair;
    }
    return null;
  }
}
