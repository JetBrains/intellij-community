/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class PreferDefaultTypeWeigher extends CompletionWeigher {
  private static final NullableLazyKey<PsiTypeParameter, CompletionLocation> TYPE_PARAMETER = NullableLazyKey.create("expectedTypes", new NullableFunction<CompletionLocation, PsiTypeParameter>() {
    @Nullable
    public PsiTypeParameter fun(final CompletionLocation location) {
      final Pair<PsiClass,Integer> pair =
          JavaSmartCompletionContributor.getTypeParameterInfo(location.getCompletionParameters().getPosition());
      if (pair == null) return null;
      return pair.first.getTypeParameters()[pair.second.intValue()];
    }
  });


  private enum MyResult {
    normal,
    exactlyExpected,
    ofDefaultType,
    exactlyDefault,
    expectedNoSelect
  }

  public MyResult weigh(@NotNull final LookupElement item, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    final Object object = item.getObject();
    if (location.getCompletionType() != CompletionType.SMART) return MyResult.normal;

    if (object instanceof PsiClass) {
      final PsiTypeParameter parameter = TYPE_PARAMETER.getValue(location);
      if (parameter != null && object.equals(PsiUtil.resolveClassInType(TypeConversionUtil.typeParameterErasure(parameter)))) {
        return MyResult.exactlyExpected;
      }
    }

    ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos == null) return MyResult.normal;

    PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
    if (itemType == null || !itemType.isValid()) return MyResult.normal;

    if (object instanceof PsiClass) {
      for (final ExpectedTypeInfo info : expectedInfos) {
        if (TypeConversionUtil.erasure(info.getType().getDeepComponentType()).equals(TypeConversionUtil.erasure(itemType))) {
          return AbstractExpectedTypeSkipper.skips(item, location) ? MyResult.expectedNoSelect : MyResult.exactlyExpected;
        }
      }
    }

    for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
      final PsiType defaultType = expectedInfo.getDefaultType();
      final PsiType expectedType = expectedInfo.getType();
      if (!expectedType.isValid()) {
        return MyResult.normal;
      }

      if (defaultType != expectedType) {
        if (defaultType.equals(itemType)) {
          return MyResult.exactlyDefault;
        }

        if (defaultType.isAssignableFrom(itemType)) {
          return MyResult.ofDefaultType;
        }
      }
      if (PsiType.VOID.equals(itemType) && PsiType.VOID.equals(expectedType)) {
        return MyResult.exactlyExpected;
      }
    }

    return MyResult.normal;
  }
}