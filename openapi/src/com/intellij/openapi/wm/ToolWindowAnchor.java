/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.wm;

public final class ToolWindowAnchor {
  public static final ToolWindowAnchor TOP = new ToolWindowAnchor("top");
  public static final ToolWindowAnchor LEFT = new ToolWindowAnchor("left");
  public static final ToolWindowAnchor BOTTOM = new ToolWindowAnchor("bottom");
  public static final ToolWindowAnchor RIGHT = new ToolWindowAnchor("right");

  private String myText;

  private ToolWindowAnchor(String text){
    myText = text;
  }

  public String toString(){
    return myText;
  }
}
