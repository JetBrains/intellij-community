package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;

public abstract class Wrap {
  public abstract void ignoreParentWraps();

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.Wrap");

  private static WrapFactory myFactory;

  public static WrapType ALWAYS = WrapType.ALWAYS;
  public static WrapType NORMAL = WrapType.NORMAL;
  public static WrapType NONE = WrapType.NONE;
  public static WrapType CHOP_DOWN_IF_LONG = WrapType.CHOP_DOWN_IF_LONG;

  static void setFactory(WrapFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  public static Wrap createWrap(WrapType type, boolean wrapFirstElement){
    return myFactory.createWrap(type, wrapFirstElement);
  }

  public static Wrap createChildWrap(final Wrap parentWrap, final WrapType wrapType, final boolean wrapFirstElement){
    return myFactory.createChildWrap(parentWrap, wrapType, wrapFirstElement);    
  }

}
