/*
 * @author max
 */
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.LeafElement;

public interface FormatterUtilHelper {
  boolean containsWhitespacesOnly(ASTNode node);
  boolean addWhitespace(final ASTNode treePrev, final LeafElement whiteSpaceElement);
}