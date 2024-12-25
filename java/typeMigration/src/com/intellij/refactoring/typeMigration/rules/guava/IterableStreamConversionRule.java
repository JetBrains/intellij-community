// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.java18StreamApi.StreamApiConstants;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public final class IterableStreamConversionRule extends TypeConversionRule {
  @Override
  public @Nullable TypeConversionDescriptorBase findConversion(PsiType from,
                                                               PsiType to,
                                                               PsiMember member,
                                                               PsiExpression context,
                                                               TypeMigrationLabeler labeler) {
    if (BaseGuavaTypeConversionRule.canConvert(from,
                                               to,
                                               CommonClassNames.JAVA_LANG_ITERABLE,
                                               StreamApiConstants.JAVA_UTIL_STREAM_STREAM)) {
      return new GuavaTypeConversionDescriptor("$it$", "$it$", context).setConvertParameterAsLambda(false);
    }
    return null;
  }
}
