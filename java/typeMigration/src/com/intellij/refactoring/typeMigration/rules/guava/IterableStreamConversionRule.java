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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.java18StreamApi.StreamApiConstants;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class IterableStreamConversionRule extends TypeConversionRule {
  @Nullable
  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
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
