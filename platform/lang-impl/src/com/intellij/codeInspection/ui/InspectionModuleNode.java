package com.intellij.codeInspection.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;

import javax.swing.*;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class InspectionModuleNode extends InspectionTreeNode{
  private final Module myModule;
  public InspectionModuleNode(final Module module) {
    super(module);
    myModule = module;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return myModule.isDisposed() ? null : ModuleType.get(myModule).getIcon();
  }

  public String getName(){
    return myModule.getName();
  }

  public String toString() {
    return getName();
  }
}
