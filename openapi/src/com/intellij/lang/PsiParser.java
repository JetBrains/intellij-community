package com.intellij.lang;

import com.intellij.psi.PsiElement;
import com.intellij.lexer.Lexer;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 2:14:05 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PsiParser {
  PsiElement parse(Lexer lexer, PsiBuilder builder);
}
