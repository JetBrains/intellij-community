package com.intellij.newCodeFormatting;

import com.intellij.openapi.diagnostic.Logger;

public abstract class Wrap {
  public static final int ALWAYS = 0;
  public static final int NORMAL = 1;
  public static final int NONE = 2;
  public static final int CHOP_DOWN_IF_LONG = 3;

  public abstract void ignoreParentWraps();

  private static final Logger LOG = Logger.getInstance("#com.intellij.newCodeFormatting.Wrap");

  private static WrapFactory myFactory;

  static void setFactory(WrapFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  public static Wrap createWrap(int type, boolean wrapFirstElement){
    return myFactory.createWrap(type, wrapFirstElement);
  }

  public static Wrap createChildWrap(final Wrap parentWrap, final int wrapType, final boolean wrapFirstElement){
    return myFactory.createChildWrap(parentWrap, wrapType, wrapFirstElement);    
  }

}
