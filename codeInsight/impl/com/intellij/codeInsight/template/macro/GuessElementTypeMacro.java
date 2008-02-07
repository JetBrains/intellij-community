
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;

import java.util.LinkedHashSet;
import java.util.Set;

public class GuessElementTypeMacro implements Macro {
  public String getName() {
    return "guessElementType";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.guess.element.type.of.container");
  }

  public String getDefaultValue() {
    return "A";
  }

  public Result calculateResult(Expression[] params, final ExpressionContext context) {
    PsiType[] types = guessTypes(params, context);
    if (types == null || types.length == 0) return null;
    return new PsiTypeResult(types[0], context.getProject());
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    PsiType[] types = guessTypes(params, context);
    if (types == null || types.length < 2) return null;
    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    for (PsiType type : types) {
      LookupItemUtil.addLookupItem(set, type);
    }
    return set.toArray(new LookupItem[set.size()]);
  }

  private PsiType[] guessTypes(Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    if (expr == null) return null;
    PsiType[] types = GuessManager.getInstance(project).guessContainerElementType(expr, new TextRange(context.getTemplateStartOffset(), context.getTemplateEndOffset()));
    for (int i = 0; i < types.length; i++) {
      PsiType type = types[i];
      if (type instanceof PsiWildcardType) {
        if (((PsiWildcardType)type).isExtends()) {
          types[i] = ((PsiWildcardType)type).getBound();
        } else {
          types[i] = PsiType.getJavaLangObject(expr.getManager(), expr.getResolveScope());
        }
      }
    }
    return types;
  }
}