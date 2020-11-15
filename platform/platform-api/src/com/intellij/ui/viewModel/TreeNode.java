// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel;

import javax.swing.*;
import java.util.List;

public class TreeNode {

  private final List<Icon> myIcons;
  private final String myPresentableName;
  private final List<TreeNode> myChildren;
  private final String myId;
  private final Runnable myExecuteNodeRunnable;

  public TreeNode(List<Icon> icons, String name, List<TreeNode> children, String id, Runnable runnable) {
    myIcons = icons;
    myPresentableName = name;
    myChildren = children;
    myId = id;
    myExecuteNodeRunnable = runnable;
  }

  public List<Icon> getIcons() {
    return myIcons;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public List<TreeNode> getChildren() {
    return myChildren;
  }

  public String getId() {
    return myId;
  }

  public Runnable executeNode() {
    return myExecuteNodeRunnable;
  }
}
