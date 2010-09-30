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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class PreferExpectedTypeWeigher extends CompletionWeigher {

  private enum MyResult {
    normal,
    expected,
    ofDefaultType,
  }

  public MyResult weigh(@NotNull final LookupElement item, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    if (location.getCompletionType() != CompletionType.BASIC) return MyResult.normal;
    if (item.getObject() instanceof PsiClass) return MyResult.normal;

    ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos == null) return MyResult.normal;

    PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
    if (itemType == null || !itemType.isValid()) return MyResult.normal;

    for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
      final PsiType defaultType = expectedInfo.getDefaultType();
      final PsiType expectedType = expectedInfo.getType();
      if (!expectedType.isValid()) {
        return MyResult.normal;
      }

      if (defaultType != expectedType && defaultType.isAssignableFrom(itemType)) {
        return MyResult.ofDefaultType;
      }
      if (expectedType.isAssignableFrom(itemType)) {
        return MyResult.expected;
      }
    }

    return MyResult.normal;
  }
}
