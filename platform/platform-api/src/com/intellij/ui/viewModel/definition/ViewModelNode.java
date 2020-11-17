// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import javax.swing.*;
import java.util.List;

public class ViewModelNode {

  private final Icon[] myIcons;
  private final String myPresentableName;
  private List<ViewModelNode> myChildren;
  private final Runnable myExecuteNodeRunnable;

  public ViewModelNode(String name, Runnable runnable, Icon... icons) {
    myIcons = icons;
    myPresentableName = name;
    myExecuteNodeRunnable = runnable;
  }

  public void setChildren(List<ViewModelNode> children) {
    myChildren = children;
  }

  public Icon[] getIcons() {
    return myIcons;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public List<ViewModelNode> getChildren() {
    return myChildren;
  }

  public Runnable executeNode() {
    return myExecuteNodeRunnable;
  }
}
