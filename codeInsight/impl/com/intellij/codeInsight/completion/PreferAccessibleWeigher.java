/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocCommentOwner;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferAccessibleWeigher extends CompletionWeigher {

  public MyEnum weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    final Object object = item.getObject();
    if (object instanceof PsiDocCommentOwner) {
      final PsiDocCommentOwner member = (PsiDocCommentOwner)object;
      if (!JavaPsiFacade.getInstance(member.getProject()).getResolveHelper().isAccessible(member, location.getCompletionParameters().getPosition(), null)) return MyEnum.INACCESSIBLE;
      if (member.isDeprecated()) return MyEnum.DEPRECATED;
    }
    return MyEnum.NORMAL;
  }

  private enum MyEnum {
    INACCESSIBLE,
    DEPRECATED,
    NORMAL
  }
}
