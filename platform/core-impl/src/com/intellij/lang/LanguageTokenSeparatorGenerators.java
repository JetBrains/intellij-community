// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.util.PsiUtilCore;

/**
 * @author yole
 */
public final class LanguageTokenSeparatorGenerators extends LanguageExtension<TokenSeparatorGenerator> {
  public static final LanguageTokenSeparatorGenerators INSTANCE = new LanguageTokenSeparatorGenerators();

  private LanguageTokenSeparatorGenerators() {
    super("com.intellij.lang.tokenSeparatorGenerator", new TokenSeparatorGenerator() {
      @Override
      public ASTNode generateWhitespaceBetweenTokens(ASTNode left, ASTNode right) {
        Language l = PsiUtilCore.getNotAnyLanguage(left);
        Language rightLang = PsiUtilCore.getNotAnyLanguage(right);
        if (rightLang.isKindOf(l)) {
          l = rightLang; // get more precise lexer
        }
        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
        if (parserDefinition != null) {
          PsiManager manager = right.getTreeParent().getPsi().getManager();
          ASTNode generatedWhitespace;
          switch (parserDefinition.spaceExistenceTypeBetweenTokens(left, right)) {
            case MUST:
              generatedWhitespace = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, " ", 0, 1, null, manager);
              break;
            case MUST_LINE_BREAK:
              generatedWhitespace = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, "\n", 0, 1, null, manager);
              break;
            default:
              generatedWhitespace = null;
          }
          return generatedWhitespace;
        }
        return null;
      }
    });
  }
}
