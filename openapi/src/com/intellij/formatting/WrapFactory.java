package com.intellij.formatting;


interface WrapFactory {
  public Wrap createWrap(int type, boolean wrapFirstElement);

  public Wrap createChildWrap(final Wrap parentWrap, final int wrapType, final boolean wrapFirstElement);
}
