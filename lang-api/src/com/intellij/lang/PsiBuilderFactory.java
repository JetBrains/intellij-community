package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public abstract class PsiBuilderFactory {
  public static PsiBuilderFactory getInstance() {
    return ServiceManager.getService(PsiBuilderFactory.class);
  }

  public abstract PsiBuilder createBuilder(Project project, ASTNode tree, Language lang, CharSequence seq);

  public abstract PsiBuilder createBuilder(Project project, ASTNode tree, Lexer lexer, Language lang, CharSequence seq);

  public abstract PsiBuilder createBuilder(final Lexer lexer, Language lang, CharSequence seq);
}
