/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class ErrorTreeNodeDescriptor extends NodeDescriptor<ErrorTreeElement>{
  private final ErrorTreeElement myElement;

  public ErrorTreeNodeDescriptor(Project project, NodeDescriptor parentDescriptor, ErrorTreeElement element) {
    super(project, parentDescriptor);
    myElement = element;
  }

  public boolean update() {
    return false;
  }

  public ErrorTreeElement getElement() {
    return myElement;
  }
}
