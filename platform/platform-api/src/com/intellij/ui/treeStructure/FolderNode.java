/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * @author kir
 */
public class FolderNode extends SimpleNode {

  private final String myFQName;
  private final String myName;

  public FolderNode(FolderNode aParent, String name) {
    super(aParent);
    myName = name;

    final String parentFqn = aParent.myFQName;
    myFQName = "".equals(parentFqn) ? myName : parentFqn + '.' + myName;
    init();
  }

  public FolderNode(Project aProject) {
    this(aProject, null);
  }

  public FolderNode(Project aProject, NodeDescriptor parent) {
    super(aProject, parent);
    myName = "";
    myFQName = "";
    init();
  }

  private void init() {
    setPlainText(myName);
    setIcons(IconLoader.getIcon("/nodes/folder.png"), IconLoader.getIcon("/nodes/folderOpen.png"));
  }

  public final SimpleNode[] getChildren() {
    throw new UnsupportedOperationException("Not Implemented in: " + getClass().getName());
  }

  public Object[] getEqualityObjects() {
    return new Object[]{myFQName, getClass()};
  }

  public String getFullyQualifiedName() {
    return myFQName;
  }
}
