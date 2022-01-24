// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * Defines a rule how one type can be converted to another, providing {@link TypeConversionDescriptorBase}.
 * 
 * @see com.intellij.refactoring.typeMigration.TypeConversionDescriptor
 */
public abstract class TypeConversionRule {
  public static final ExtensionPointName<TypeConversionRule> EP_NAME = ExtensionPointName.create("com.intellij.conversion.rule");

  /**
   * Defines the conversion
   * 
   * @param member member which is called on {@code from} in this {@code context}; can be used to map methods in {@code from} to methods in {@code to}
   * 
   * @return null when it's impossible to convert {@code from} type to {@code to} type by this rule,
   *         conversion description otherwise
   */
  @Nullable
  public abstract TypeConversionDescriptorBase findConversion(final PsiType from,
                                                              final PsiType to,
                                                              final PsiMember member,
                                                              final PsiExpression context,
                                                              final TypeMigrationLabeler labeler);


  /**
   * @return type parameters mapping between {@code from} and {@code to}
   */
  @Nullable
  public Pair<PsiType, PsiType> bindTypeParameters(PsiType from, PsiType to, final PsiMethod method, final PsiExpression context,
                                                   final TypeMigrationLabeler labeler) {
    return null;
  }

  /**
   * @return true if {@code null} initializer should be migrated as well
   */
  public boolean shouldConvertNullInitializer(PsiType from, PsiType to, PsiExpression context) {
    return false;
  }
}