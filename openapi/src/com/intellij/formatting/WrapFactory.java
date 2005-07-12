package com.intellij.formatting;


interface WrapFactory {
  public Wrap createWrap(WrapType type, boolean wrapFirstElement);

  public Wrap createChildWrap(final Wrap parentWrap, final WrapType wrapType, final boolean wrapFirstElement);
}
