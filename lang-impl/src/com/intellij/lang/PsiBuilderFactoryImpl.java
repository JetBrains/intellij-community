package com.intellij.lang;

import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class PsiBuilderFactoryImpl extends PsiBuilderFactory {
  public PsiBuilder createBuilder(Project project, final ASTNode tree, final Language lang, final CharSequence seq) {
    return new PsiBuilderImpl(lang, null, tree, project, seq);
  }

  public PsiBuilder createBuilder(Project project, final ASTNode tree, final Lexer lexer, final Language lang, final CharSequence seq) {
    return new PsiBuilderImpl(lang, lexer, tree, project, seq);
  }

  public PsiBuilder createBuilder(final Lexer lexer, final Language lang, final CharSequence seq) {
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    return new PsiBuilderImpl(lexer, parserDefinition.getWhitespaceTokens(), parserDefinition.getCommentTokens(), seq);
  }
}
