/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Comparing;

import javax.swing.*;

public final class KeyboardShortcut extends Shortcut{
  private final KeyStroke myFirstKeyStroke;
  private final KeyStroke mySecondKeyStroke;

  /**
   * @throws IllegalArgumentException if <code>firstKeyStroke</code> is <code>null</code>
   */
  public KeyboardShortcut(KeyStroke firstKeyStroke, KeyStroke secondKeyStroke){
    if (firstKeyStroke == null) {
      throw new IllegalArgumentException("firstKeystroke cannot be null");
    }
    myFirstKeyStroke = firstKeyStroke;
    mySecondKeyStroke = secondKeyStroke;
  }

  public KeyStroke getFirstKeyStroke(){
    return myFirstKeyStroke;
  }

  public KeyStroke getSecondKeyStroke(){
    return mySecondKeyStroke;
  }

  public int hashCode(){
    int hashCode=myFirstKeyStroke.hashCode();
    if(mySecondKeyStroke!=null){
      hashCode+=mySecondKeyStroke.hashCode();
    }
    return hashCode;
  }

  public boolean equals(Object obj){
    if (!(obj instanceof KeyboardShortcut)){
      return false;
    }
    KeyboardShortcut second = (KeyboardShortcut)obj;
    if (!Comparing.equal(myFirstKeyStroke, second.myFirstKeyStroke)){
      return false;
    }
    if (!Comparing.equal(mySecondKeyStroke, second.mySecondKeyStroke)){
      return false;
    }
    return true;
  }
}
