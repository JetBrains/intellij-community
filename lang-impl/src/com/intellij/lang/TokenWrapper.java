/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

public class TokenWrapper extends IElementType {
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
}
