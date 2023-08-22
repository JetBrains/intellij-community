// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.classMembers;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;

import java.util.HashMap;

public final class MemberInfoTooltipManager<T extends PsiElement, M extends MemberInfoBase<T>> {
  private final HashMap<M, @NlsContexts.Tooltip String> myTooltips = new HashMap<>();
  private final TooltipProvider<T, M> myProvider;

  public interface TooltipProvider<T extends PsiElement, M extends MemberInfoBase<T>> {
    @NlsContexts.Tooltip String getTooltip(M memberInfo);
  }

  public MemberInfoTooltipManager(TooltipProvider<T, M> provider) {
    myProvider = provider;
  }

  public void invalidate() {
    myTooltips.clear();
  }

  public @NlsContexts.Tooltip String getTooltip(M member) {
    if(myTooltips.containsKey(member)) {
      return myTooltips.get(member);
    }
    String tooltip = myProvider.getTooltip(member);
    myTooltips.put(member, tooltip);
    return tooltip;
  }
}
