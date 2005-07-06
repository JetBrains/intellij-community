package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;

public abstract class Indent {
  private static IndentFactory myFactory;

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.Indent");

  static void setFactory(IndentFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  public static Indent createNormalIndent() {
    return myFactory.createNormalIndent();
  }

  public static Indent createNormalIndent(int count){
    return myFactory.createNormalIndent(count);
  }

  public static Indent createAbsoluteNormalIndent(){
    return myFactory.createAbsoluteNormalIndent();
  }

  public static Indent getNoneIndent(){
    return myFactory.getNoneIndent();
  }

  public static Indent createAbsoluteNoneIndent(){
    return myFactory.createAbsoluteNoneIndent();
  }

  public static Indent createAbsoluteLabelIndent(){
    return myFactory.createAbsoluteLabelIndent();
  }

  public static Indent createLabelIndent(){
    return myFactory.createLabelIndent();
  }

  public static Indent createContinuationIndent(){
    return myFactory.createContinuationIndent();
  }

  public static Indent createContinuationWithoutFirstIndent() {//is default
    return myFactory.createContinuationWithoutFirstIndent();
  }

  public static Indent createSpaceIndent(final int spaces){
    return myFactory.createSpaceIndent(spaces);
  }

}
