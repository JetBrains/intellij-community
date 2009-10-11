/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
