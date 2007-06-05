package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public abstract class VariableTypeMacroBase implements Macro {
  @Nullable
  protected abstract PsiElement[] getVariables(Expression[] params, final ExpressionContext context);

  public LookupItem[] calculateLookupItems(Expression[] params, final ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    for (PsiElement element : vars) {
      LookupItemUtil.addLookupItem(set, element, "");
    }
    return set.toArray(new LookupItem[set.size()]);
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new PsiElementResult(vars[0]);
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public String getDefaultValue() {
    return "a";
  }

}
