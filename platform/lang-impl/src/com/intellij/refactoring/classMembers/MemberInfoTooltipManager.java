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

package com.intellij.refactoring.classMembers;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;

import java.util.HashMap;

public class MemberInfoTooltipManager<T extends PsiElement, M extends MemberInfoBase<T>> {
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
