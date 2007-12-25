package com.intellij.codeInsight.editorActions;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.JavaTokenType;

public class JavaTypedHandlerUtil {
  private JavaTypedHandlerUtil() {
  }

  public static boolean isTokenInvalidInsideReference(final IElementType tokenType) {
    return tokenType == JavaTokenType.SEMICOLON ||
           tokenType == JavaTokenType.LBRACE ||
           tokenType == JavaTokenType.RBRACE;
  }
}
