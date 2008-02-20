package com.intellij.lang;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface LiteralEscaper {
  String getEscapedText(PsiElement context, String originalText);
}
