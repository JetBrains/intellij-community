/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILeafElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class TokenWrapper extends IElementType implements ILeafElementType {
  private final IElementType myDelegate;
  private final String myValue;

  public TokenWrapper(IElementType delegate, CharSequence value) {
    super("Wrapper", delegate.getLanguage(), false);
    myDelegate = delegate;
    myValue = value.toString();
  }

  public IElementType getDelegate() {
    return myDelegate;
  }

  public String getValue() {
    return myValue;
  }

  @Override
  public String toString() {
    return "Wrapper (" + myDelegate + ")";
  }

  @NotNull
  public ASTNode createLeafNode(CharSequence text, int start, int end, CharTable table) {
    return new ForeignLeafPsiElement(myDelegate, myValue, table);
  }
}