/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
public class PreferExpectedTypeWeigher extends CompletionWeigher {
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
    ofDefaultType,
    expected,
    expectedNoSelect
  }

  public MyResult weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    final Object object = item.getObject();
    if (object instanceof PsiClass && location.getCompletionType() != CompletionType.SMART) return MyResult.normal;

    if (object instanceof PsiClass) {
      final PsiTypeParameter parameter = TYPE_PARAMETER.getValue(location);
      if (parameter != null && object.equals(PsiUtil.resolveClassInType(TypeConversionUtil.typeParameterErasure(parameter)))) {
        return MyResult.expected;
      }
    }

    ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos == null) return MyResult.normal;

    final PsiType itemType = JavaCompletionUtil.getPsiType(object);
    if (itemType == null) return MyResult.normal;

    if (object instanceof PsiClass) {
      for (final ExpectedTypeInfo info : expectedInfos) {
        if (TypeConversionUtil.erasure(info.getType().getDeepComponentType()).equals(TypeConversionUtil.erasure(itemType))) {
          return SkipAbstractExpectedTypeWeigher.getSkippingStatus(item, location) != SkipAbstractExpectedTypeWeigher.Result.ACCEPT ? MyResult.expectedNoSelect : MyResult.expected;
        }
      }
    }

    for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
      final PsiType defaultType = expectedInfo.getDefaultType();
      final PsiType expectedType = expectedInfo.getType();
      if (defaultType != expectedType && defaultType.isAssignableFrom(itemType)) {
        return MyResult.ofDefaultType;
      }
      if (location.getCompletionType() == CompletionType.BASIC && expectedType.isAssignableFrom(itemType)) {
        return MyResult.expected;
      }
    }

    return MyResult.normal;
  }
}
