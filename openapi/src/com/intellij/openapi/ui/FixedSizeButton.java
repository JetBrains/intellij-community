/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

/**
 * This class represents non resizable, nonfocusable button with the
 * same height and length.
 */
public class FixedSizeButton extends JButton {
  private final int mySize;
  private final JComponent myComponent;

  private FixedSizeButton(int size,JComponent component){
    super(IconLoader.getIcon("/general/ellipsis.png"));
    mySize=size;
    myComponent=component;
    setMargin(new Insets(0, 0, 0, 0));
    setDefaultCapable(false);
    setFocusable(false);
  }

  /**
   * Creates the <code>FixedSizeButton</code> with specified size.
   * @throws java.lang.IllegalArgumentException if <code>size</code> isn't
   * positive integer number.
   */
  public FixedSizeButton(int size){
    this(size,null);
    if(size<=0){
      throw new IllegalArgumentException("wrong size: "+size);
    }
  }

  /**
   * Creates the <code>FixedSizeButton</code> which size is equals to
   * <code>component.getPreferredSize().height</code>. It is very convenient
   * way to create "browse" like button near the text fields.
   */
  public FixedSizeButton(JComponent component) {
    this(-1,component);
    if(component==null){
      throw new IllegalArgumentException("component cannot be null");
    }
  }

  public Dimension getMinimumSize(){
    return getPreferredSize();
  }

  public Dimension getMaximumSize(){
    return getPreferredSize();
  }

  public Dimension getPreferredSize(){
    if(myComponent!=null){
      int height=myComponent.getPreferredSize().height;
      return new Dimension(height,height);
    }else if(mySize!=-1){
      return new Dimension(mySize,mySize);
    }else{
      throw new IllegalStateException("myComponent==null and mySize==-1");
    }
  }

  public JComponent getAttachedComponent() {

    return myComponent;
  }
}

