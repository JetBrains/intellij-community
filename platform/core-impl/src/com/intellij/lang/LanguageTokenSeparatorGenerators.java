// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class LanguageTokenSeparatorGenerators extends LanguageExtension<TokenSeparatorGenerator> {
  public static final LanguageTokenSeparatorGenerators INSTANCE = new LanguageTokenSeparatorGenerators();

  private LanguageTokenSeparatorGenerators() {
    super("com.intellij.lang.tokenSeparatorGenerator", new DefaultTokenSeparatorGenerator());
  }

  public static class DefaultTokenSeparatorGenerator implements TokenSeparatorGenerator {
    @Override
    public ASTNode generateWhitespaceBetweenTokens(@Nullable ASTNode left, @NotNull ASTNode right) {
      Language leftLang = PsiUtilCore.getNotAnyLanguage(left);
      Language rightLang = PsiUtilCore.getNotAnyLanguage(right);
      if (rightLang.isKindOf(leftLang)) {
        leftLang = rightLang; // get more precise lexer
      }
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(leftLang);
      if (parserDefinition != null) {
        ASTNode generatedWhitespace;
        switch (parserDefinition.spaceExistenceTypeBetweenTokens(left, right)) {
          case MUST:
            generatedWhitespace = createWhiteSpace(left, right);
            break;
          case MUST_LINE_BREAK:
            generatedWhitespace = createNewLine(left, right);
            break;
          default:
            generatedWhitespace = null;
        }
        return generatedWhitespace;
      }
      return null;
    }

    protected @NotNull LeafElement createNewLine(@Nullable ASTNode left, @NotNull ASTNode right) {
      PsiManager manager = right.getTreeParent().getPsi().getManager();
      return Factory.createSingleLeafElement(TokenType.WHITE_SPACE, "\n", 0, 1, null, manager);
    }

    protected @NotNull LeafElement createWhiteSpace(@Nullable ASTNode left, @NotNull ASTNode right) {
      PsiManager manager = right.getTreeParent().getPsi().getManager();
      return Factory.createSingleLeafElement(TokenType.WHITE_SPACE, " ", 0, 1, null, manager);
    }
  }
}
