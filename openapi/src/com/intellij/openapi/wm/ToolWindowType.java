/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.wm;

/**
 * This is enumeration of all posiible types of tool windows.
 */
public final class ToolWindowType {
  public static final ToolWindowType DOCKED = new ToolWindowType("docked");
  public static final ToolWindowType FLOATING = new ToolWindowType("floating");
  public static final ToolWindowType SLIDING = new ToolWindowType("sliding");

  private String myText;

  private ToolWindowType(String text){
    myText = text;
  }

  public String toString(){
    return myText;
  }
}
