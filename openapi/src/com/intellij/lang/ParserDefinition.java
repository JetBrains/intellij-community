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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 7:57:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ParserDefinition {
  Lexer createLexer(Project project);
  PsiParser createParser(Project project);

  IFileElementType getFileNodeType();

  @NotNull
  TokenSet getWhitespaceTokens();
  @NotNull
  TokenSet getCommentTokens();

  PsiElement createElement(ASTNode node);
  PsiFile createFile(Project project, VirtualFile file);
  PsiFile createFile(Project project, String name, CharSequence text);
}
