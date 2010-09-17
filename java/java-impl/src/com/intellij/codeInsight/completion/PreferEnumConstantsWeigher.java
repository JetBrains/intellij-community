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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class PreferEnumConstantsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    if (item.getObject() instanceof PsiEnumConstant) return 2;

    final CompletionParameters parameters = location.getCompletionParameters();
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return 1;

    if (PsiKeyword.TRUE.equals(item.getLookupString()) || PsiKeyword.FALSE.equals(item.getLookupString())) {
      boolean inReturn = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiReturnStatement.class, false, PsiMember.class) != null;
      return inReturn ? 2 : 0;
    }

    return 1;
  }
}
