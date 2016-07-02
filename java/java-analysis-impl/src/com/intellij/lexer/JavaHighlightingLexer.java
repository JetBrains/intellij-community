/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class JavaHighlightingLexer extends LayeredLexer {
  private boolean myAtStart = true;
  private boolean myModuleInfo = false;

  public JavaHighlightingLexer(@NotNull LanguageLevel languageLevel) {
    super(JavaParserDefinition.createLexer(languageLevel));

    registerSelfStoppingLayer(new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL),
                              new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);

    registerSelfStoppingLayer(new StringLiteralLexer('\'', JavaTokenType.STRING_LITERAL),
                              new IElementType[]{JavaTokenType.CHARACTER_LITERAL}, IElementType.EMPTY_ARRAY);

    LayeredLexer docLexer = new LayeredLexer(JavaParserDefinition.createDocLexer(languageLevel));
    HtmlHighlightingLexer htmlLexer = new HtmlHighlightingLexer(null);
    htmlLexer.setHasNoEmbeddments(true);
    docLexer.registerLayer(htmlLexer, JavaDocTokenType.DOC_COMMENT_DATA);
    registerSelfStoppingLayer(docLexer, new IElementType[]{JavaDocElementType.DOC_COMMENT}, IElementType.EMPTY_ARRAY);
  }

  @Override
  public IElementType getTokenType() {
    IElementType t = super.getTokenType();

    if (myAtStart && !ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(t)) {
      myAtStart = false;
      myModuleInfo = t == JavaTokenType.IDENTIFIER && PsiKeyword.MODULE.equals(getTokenText());
    }

    if (myModuleInfo && t == JavaTokenType.IDENTIFIER) {
      String text = getTokenText();
      if (PsiKeyword.MODULE.equals(text)) t = JavaTokenType.MODULE_KEYWORD;
      else if (PsiKeyword.REQUIRES.equals(text)) t = JavaTokenType.REQUIRES_KEYWORD;
      else if (PsiKeyword.EXPORTS.equals(text)) t = JavaTokenType.EXPORTS_KEYWORD;
      else if (PsiKeyword.USES.equals(text)) t = JavaTokenType.USES_KEYWORD;
      else if (PsiKeyword.PROVIDES.equals(text)) t = JavaTokenType.PROVIDES_KEYWORD;
      else if (PsiKeyword.TO.equals(text)) t = JavaTokenType.TO_KEYWORD;
      else if (PsiKeyword.WITH.equals(text)) t = JavaTokenType.WITH_KEYWORD;
    }

    return t;
  }
}