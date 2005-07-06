package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;

public abstract class Alignment {
  private static AlignmentFactory myFactory;

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.Alignment");

  static void setFactory(AlignmentFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  public static Alignment createAlignment(){
    return myFactory.createAlignment();
  }
}
