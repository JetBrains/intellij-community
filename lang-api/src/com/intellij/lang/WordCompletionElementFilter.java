/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

public interface WordCompletionElementFilter {
  boolean isWordCompletionEnabledIn(IElementType element);
}