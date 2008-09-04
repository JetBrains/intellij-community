package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public abstract class VariableTypeMacroBase implements Macro {
  @Nullable
  protected abstract PsiElement[] getVariables(Expression[] params, final ExpressionContext context);

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    for (PsiElement element : vars) {
      JavaTemplateUtil.addElementLookupItem(set, element);
    }
    return set.toArray(new LookupItem[set.size()]);
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new JavaPsiElementResult(vars[0]);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }

  public String getDefaultValue() {
    return "a";
  }

}
