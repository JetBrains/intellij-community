package com.intellij.uiDesigner.shared;

import javax.swing.*;
import javax.swing.border.Border;

/**
 * @author Vladimir Kondratyev
 */
public final class BorderType {
  public static final BorderType NONE = new BorderType("none", "None",null,null);
  public static final BorderType BEVEL_LOWERED = new BorderType("bevel-lowered", "Bevel Lowered", BorderFactory.createLoweredBevelBorder(), "createLoweredBevelBorder");
  public static final BorderType BEVEL_RAISED = new BorderType("bevel-raised", "Bevel Raised", BorderFactory.createRaisedBevelBorder(), "createRaisedBevelBorder");
  public static final BorderType ETCHED = new BorderType("etched", "Etched", BorderFactory.createEtchedBorder(), "createEtchedBorder");

  private final String myId;
  private final String myName;
  private final Border myBorder;
  private final String myBorderFactoryMethodName;

  private BorderType(final String id,final String name,final Border border,final String borderFactoryMethodName){
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

  public Border createBorder(final String title){
    if (title != null) {
      return BorderFactory.createTitledBorder(myBorder, title);
    }
    else {
      return myBorder;
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
    if(NONE.getId().equals(name)){
      return NONE;
    }
    else if(BEVEL_LOWERED.getId().equals(name)){
      return BEVEL_LOWERED;
    }
    else if(BEVEL_RAISED.getId().equals(name)){
      return BEVEL_RAISED;
    }
    else if(ETCHED.getId().equals(name)){
      return ETCHED;
    }
    else{
      throw new IllegalArgumentException("unknown type: "+name);
    }
  }
}
