package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public abstract class PsiBuilderFactory {
  public static PsiBuilderFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiBuilderFactory.class);
  }

  public abstract PsiBuilder createBuilder(ASTNode tree, Language lang, CharSequence seq);

  public abstract PsiBuilder createBuilder(ASTNode tree, Lexer lexer, Language lang, CharSequence seq);
}
