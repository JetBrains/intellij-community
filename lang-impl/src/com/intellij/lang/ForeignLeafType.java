/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILeafElementType;
import org.jetbrains.annotations.NotNull;

public class ForeignLeafType extends TokenWrapper implements ILeafElementType {
  public ForeignLeafType(IElementType delegate, CharSequence value) {
    super(delegate, value);
  }

  @NotNull
  public ASTNode createLeafNode(CharSequence leafText) {
    return new ForeignLeafPsiElement(getDelegate(), getValue());
  }
}
