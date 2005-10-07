/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import jetbrains.fabrique.model.FProject;
import jetbrains.fabrique.model.FProject;
import com.intellij.ide.util.treeView.NodeDescriptor;

public abstract class CachingSimpleNode extends SimpleNode {

  SimpleNode[] myChildren;

  protected CachingSimpleNode() {
  }

  protected CachingSimpleNode(FProject project) {
    super(project);
  }

  protected CachingSimpleNode(SimpleNode aParent) {
    super(aParent);
  }

  protected CachingSimpleNode(FProject aProject, NodeDescriptor aParentDescriptor) {
    super(aProject, aParentDescriptor);
  }

  public final SimpleNode[] getChildren() {
    if (myChildren == null) {
      myChildren = buildChildren();
    }

    return myChildren;
  }

  protected abstract SimpleNode[] buildChildren();

  public void cleanUpCache() {
    myChildren = null;
  }

  SimpleNode[] getCached() {
    return myChildren;
  }

}
