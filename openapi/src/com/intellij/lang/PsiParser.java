package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 2:14:05 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PsiParser {
  ASTNode parse(IElementType root, PsiBuilder builder);
}
