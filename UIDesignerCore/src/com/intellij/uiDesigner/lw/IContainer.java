package com.intellij.uiDesigner.lw;

public interface IContainer extends IComponent{
  int getComponentCount();
  IComponent getComponent(int index);
  boolean isXY();
}
