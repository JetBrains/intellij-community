package com.intellij.uiDesigner.shared;

import com.intellij.uiDesigner.core.AbstractLayout;

import java.awt.*;

public class XYLayoutManager extends AbstractLayout {
  public XYLayoutManager(){
  }

  public Dimension maximumLayoutSize(final Container target){
    throw new UnsupportedOperationException();
  }

  public Dimension preferredLayoutSize(final Container parent){
    throw new UnsupportedOperationException();
  }

  public Dimension minimumLayoutSize(final Container parent){
    throw new UnsupportedOperationException();
  }

  public void layoutContainer(final Container parent){
    throw new UnsupportedOperationException();
  }
  
  public void setPreferredSize(final Dimension size){
    throw new UnsupportedOperationException();
  }

  public final void invalidateLayout(final Container target){
  }
}
