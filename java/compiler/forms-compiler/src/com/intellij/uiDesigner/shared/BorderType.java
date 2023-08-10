// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.shared;

import com.intellij.uiDesigner.compiler.UnexpectedFormElementException;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public final class BorderType {
  public static final BorderType NONE = new BorderType("none", "None",null,null);
  private static final BorderType BEVEL_LOWERED = new BorderType("bevel-lowered", "Bevel Lowered", BorderFactory.createLoweredBevelBorder(), "createLoweredBevelBorder");
  private static final BorderType BEVEL_RAISED = new BorderType("bevel-raised", "Bevel Raised", BorderFactory.createRaisedBevelBorder(), "createRaisedBevelBorder");
  private static final BorderType ETCHED = new BorderType("etched", "Etched", BorderFactory.createEtchedBorder(), "createEtchedBorder");
  public static final BorderType LINE = new BorderType("line", "Line", BorderFactory.createLineBorder(Color.BLACK), "createLineBorder");
  public static final BorderType EMPTY = new BorderType("empty", "Empty", BorderFactory.createEmptyBorder(0, 0, 0, 0), "createEmptyBorder");

  private final String myId;
  private final String myName;
  private final Border myBorder;
  private final String myBorderFactoryMethodName;

  private BorderType(final String id, final String name, final Border border, final String borderFactoryMethodName) {
    myId=id;
    myName=name;
    myBorder=border;
    myBorderFactoryMethodName = borderFactoryMethodName;
  }

  public String getId(){
    return myId;
  }

  public String getName(){
    return myName;
  }

  public Border createBorder(@Nls(capitalization = Nls.Capitalization.Title) final String title,
                             final int titleJustification,
                             final int titlePosition,
                             final Font titleFont,
                             final Color titleColor,
                             final Insets borderSize, 
                             final Color borderColor) {
    Border baseBorder = myBorder;
    if (equals(EMPTY) && borderSize != null) {
      baseBorder = BorderFactory.createEmptyBorder(borderSize.top, borderSize.left, borderSize.bottom, borderSize.right);
    }
    else if (equals(LINE) && borderColor != null) {
      baseBorder = BorderFactory.createLineBorder(borderColor);
    }

    if (title != null) {
      return BorderFactory.createTitledBorder(baseBorder, title, titleJustification, titlePosition, titleFont, titleColor);
    }
    else {
      return baseBorder;
    }
  }

  public String getBorderFactoryMethodName(){
    return myBorderFactoryMethodName;
  }

  public boolean equals(final Object o){
    if (o instanceof BorderType){
      return myId.equals(((BorderType)o).myId);
    }
    return false;
  }

  public int hashCode(){
    return 0;
  }

  public static BorderType valueOf(final String name){
    BorderType[] allTypes = getAllTypes();
    for (BorderType type : allTypes) {
      if (type.getId().equals(name)) return type;
    }
    throw new UnexpectedFormElementException("unknown type: "+name);
  }

  public static BorderType[] getAllTypes() {
    return new BorderType[]{
          NONE,
          EMPTY,
          BEVEL_LOWERED,
          BEVEL_RAISED,
          ETCHED,
          LINE
        };
  }
}
