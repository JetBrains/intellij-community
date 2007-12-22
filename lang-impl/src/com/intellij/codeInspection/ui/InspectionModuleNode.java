/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.ui;

import com.intellij.openapi.module.Module;

import javax.swing.*;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class InspectionModuleNode extends InspectionTreeNode{
  private Module myModule;
  public InspectionModuleNode(final Module module) {
    super(module);
    myModule = module;
  }

  public Icon getIcon(boolean expanded) {
    return myModule.getModuleType().getNodeIcon(expanded);
  }

  public String getName(){
    return myModule.getName();
  }

  public String toString() {
    return getName();
  }
}
