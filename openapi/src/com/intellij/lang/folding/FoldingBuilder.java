package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 1, 2005
 * Time: 12:10:19 AM
 * To change this template use File | Settings | File Templates.
 */
public interface FoldingBuilder {
  FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document);

  String getPlaceholderText(ASTNode node);

  boolean isCollapsedByDefault(ASTNode node);
}
