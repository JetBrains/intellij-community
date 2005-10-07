/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import jetbrains.fabrique.model.FProject;
import com.intellij.ide.util.treeView.NodeDescriptor;

import java.util.List;
import java.util.ArrayList;

public abstract class SimpleMutableNode extends CachingSimpleNode {

  private List myChildren = new ArrayList();

  public SimpleMutableNode() {
  }

  protected SimpleMutableNode(FProject project) {
    super(project);
  }

  public SimpleMutableNode(SimpleNode aParent) {
    super(aParent);
  }

  public SimpleMutableNode(FProject aProject, NodeDescriptor aParentDescriptor) {
    super(aProject, aParentDescriptor);
  }

  protected final SimpleNode[] buildChildren() {
    return (SimpleNode[]) myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  public final SimpleNode add(SimpleNode child) {
    myChildren.add(child);
    cleanUpCache();
    return child;
  }


  public final SimpleNode[] addAll(SimpleNode[] children) {
    for (int i = 0; i < children.length; i++) {
      add(children[i]);
    }

    return children;
  }

  public final void remove(SimpleNode child) {
    myChildren.remove(child);
    cleanUpCache();
  }

  public final void clear() {
    myChildren.clear();
    cleanUpCache();
  }
}