/*
 * User: anna
 * Date: 08-Aug-2008
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
}