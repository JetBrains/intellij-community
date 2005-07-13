/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
  @NotNull
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
   * @param project the project to which the file belongs.
   * @param file    the file for which the PSI element is created.
   * @return the PSI file element.
   */
  PsiFile createFile(Project project, VirtualFile file);

  /**
   * Creates a PSI element for the file with the specified name and contents.
   *
   * @param project the project to which the file belongs.
   * @param name    The name of the file.
   * @param text    The contents of the file.
   * @return the PSI file element.
   */
  PsiFile createFile(Project project, String name, CharSequence text);
}
