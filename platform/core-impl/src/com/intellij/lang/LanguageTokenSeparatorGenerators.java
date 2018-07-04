/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang;

import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.util.PsiUtilCore;

/**
 * @author yole
 */
public class LanguageTokenSeparatorGenerators extends LanguageExtension<TokenSeparatorGenerator> {
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
          //noinspection EnumSwitchStatementWhichMissesCases
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
