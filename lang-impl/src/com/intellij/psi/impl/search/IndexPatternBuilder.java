package com.intellij.psi.impl.search;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface IndexPatternBuilder {
  ExtensionPointName<IndexPatternBuilder> EP_NAME = ExtensionPointName.create("com.intellij.indexPatternBuilder");

  @Nullable
  Lexer getIndexingLexer(PsiFile file);
  @Nullable
  TokenSet getCommentTokenSet(PsiFile file);
  int getCommentStartDelta(IElementType tokenType);
  int getCommentEndDelta(IElementType tokenType);
}
