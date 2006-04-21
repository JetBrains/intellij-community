/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the implementation of a parser for a custom language.
 *
 * @see com.intellij.lang.Language#getParserDefinition()
 */

public interface ParserDefinition {
  /**
   * Returns the lexer for lexing files in the specified project. This lexer does not need to support incremental relexing - it is always
   * called for the entire file.
   *
   * @param project the project to which the lexer is connected.
   * @return the lexer instance.
   */
  @NotNull
  Lexer createLexer(Project project);

  /**
   * Returns the parser for parsing files in the specified project.
   *
   * @param project the project to which the parser is connected.
   * @return the parser instance.
   */
  PsiParser createParser(Project project);

  /**
   * Returns the element type of the node describing a file in the specified language.
   *
   * @return the file node element type.
   */
  IFileElementType getFileNodeType();

  /**
   * Returns the set of token types which are treated as whitespace by the PSI builder.
   * Tokens of those types are automatically skipped by PsiBuilder. Whitespace elements
   * on the bounds of nodes built by PsiBuilder are automatically excluded from the text
   * range of the nodes.
   *
   * @return the set of whitespace token types.
   */
  @NotNull
  TokenSet getWhitespaceTokens();

  /**
   * Returns the set of token types which are treated as comments by the PSI builder.
   * Tokens of those types are automatically skipped by PsiBuilder. Also, To Do patterns
   * are searched in the text of tokens of those types.
   *
   * @return the set of comment token types.
   */
  @NotNull
  TokenSet getCommentTokens();

  /**
   * Creates a PSI element for the specified AST node. The AST tree is a simple, semantic-free
   * tree of AST nodes which is built during the PsiBuilder parsing pass. The PSI tree is built
   * over the AST tree and includes elements of different types for different language constructs.
   *
   * @param node the node for which the PSI element should be returned.
   * @return the PSI element matching the element type of the AST node.
   */
  @NotNull
  PsiElement createElement(ASTNode node);

  /**
   * Creates a PSI element for the specified virtual file.
   *
   * @param viewProvider
   * @return the PSI file element.
   */
  PsiFile createFile(FileViewProvider viewProvider);

  SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right);

  enum SpaceRequirements {
    MAY,
    MUST,
    MUST_NOT,
    MUST_LINE_BREAK,
  }
}
