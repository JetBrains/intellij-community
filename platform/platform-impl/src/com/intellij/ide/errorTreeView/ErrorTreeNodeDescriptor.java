// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 */
public class ErrorTreeNodeDescriptor extends NodeDescriptor<ErrorTreeElement>{
  private final ErrorTreeElement myElement;

  public ErrorTreeNodeDescriptor(Project project, NodeDescriptor parentDescriptor, ErrorTreeElement element) {
    super(project, parentDescriptor);
    myElement = element;
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public ErrorTreeElement getElement() {
    return myElement;
  }
}
