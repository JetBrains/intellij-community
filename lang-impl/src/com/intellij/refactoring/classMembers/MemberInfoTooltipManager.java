package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;

/**
 * @author dsl
 */
public class MemberInfoTooltipManager<T extends PsiElement, M extends MemberInfoBase<T>> {
  private final HashMap<M, String> myTooltips = new HashMap<M, String>();
  private final TooltipProvider<T, M> myProvider;

  public interface TooltipProvider<T extends PsiElement, M extends MemberInfoBase<T>> {
    String getTooltip(M memberInfo);
  }

  public MemberInfoTooltipManager(TooltipProvider<T, M> provider) {
    myProvider = provider;
  }

  public void invalidate() {
    myTooltips.clear();
  }

  public String getTooltip(M member) {
    if(myTooltips.keySet().contains(member)) {
      return myTooltips.get(member);
    }
    String tooltip = myProvider.getTooltip(member);
    myTooltips.put(member, tooltip);
    return tooltip;
  }
}
