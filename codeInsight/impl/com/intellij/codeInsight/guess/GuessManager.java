
package com.intellij.codeInsight.guess;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;

public abstract class GuessManager {
  public static GuessManager getInstance(Project project) {
    return ServiceManager.getService(project, GuessManager.class);
  }

  public abstract PsiType[] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore);

  public abstract PsiType[] guessTypeToCast(PsiExpression expr);
}