/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferEnumConstantsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
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
