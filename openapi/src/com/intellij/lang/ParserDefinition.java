package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 7:57:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ParserDefinition {
  Lexer createLexer();
  PsiParser createParser();

  IElementType getFileNodeType();

  TokenSet getWhitespaceTokens();
  TokenSet getCommentTokens();

  PsiFile createFile(VirtualFile file);
  PsiFile createFile(String name, CharSequence text);
}
