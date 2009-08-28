package com.intellij.psi.impl.source.parsing;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.Lexer;

public interface TokenProcessor {
  @Nullable
  TreeElement process(Lexer lexer, ParsingContext context);

  boolean isTokenValid(IElementType tokenType);
}
