package com.intellij.lang.folding;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 1, 2005
 * Time: 12:10:19 AM
 * To change this template use File | Settings | File Templates.
 */
public interface FoldingBuilder {
  FoldingDescriptor[] buildFoldRegions(PsiElement file, Document document);

  String getPlaceholderText(PsiElement elt);

  boolean isCollapsedByDefault(PsiElement elt);
}
