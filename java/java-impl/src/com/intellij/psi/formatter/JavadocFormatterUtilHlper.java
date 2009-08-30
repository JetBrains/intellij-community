/*
 * @author max
 */
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;

public class JavadocFormatterUtilHlper implements FormatterUtilHelper {
  public boolean addWhitespace(final ASTNode treePrev, final LeafElement whiteSpaceElement) {
    return false;
  }

  public boolean containsWhitespacesOnly(final ASTNode node) {
    return node.getElementType() == ElementType.DOC_COMMENT_DATA && node.textContains('\n') && node.getText().trim().length() == 0;
  }
}