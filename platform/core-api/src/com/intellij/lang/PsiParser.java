// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * The plugin side of a custom language parser. Receives tokens returned from
 * {@link com.intellij.lexer.Lexer lexer} and builds an AST tree out of them.
 *
 * @see ParserDefinition#createParser(com.intellij.openapi.project.Project)
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html">Implementing Parser and PSI (IntelliJ Platform Docs)</a>
 */
public interface PsiParser {

  /**
   * Parses the contents of the specified PSI builder and returns an AST tree with the
   * specified type of root element. The PSI builder contents is the entire file
   * or (if chameleon tokens are used) the text of a chameleon token which needs to
   * be reparsed.
   *
   * @param root    the type of the root element in the AST tree.
   * @param builder the builder which is used to retrieve the original file tokens and build the AST tree.
   * @return the root of the resulting AST tree.
   */
  @NotNull
  ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder);
}
