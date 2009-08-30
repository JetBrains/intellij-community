
package com.intellij.codeInsight.guess;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class GuessManager {
  public static GuessManager getInstance(Project project) {
    return ServiceManager.getService(project, GuessManager.class);
  }

  public abstract PsiType[] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore);

  public abstract PsiType[] guessTypeToCast(PsiExpression expr);

  @NotNull 
  public abstract Map<PsiExpression, PsiType> getDataFlowExpressionTypes(@NotNull PsiExpression forPlace);

  @Nullable
  public PsiType getDataFlowCastedExpressionType(@NotNull PsiExpression expr) {
    return getDataFlowExpressionTypes(expr).get(expr);
  }
}