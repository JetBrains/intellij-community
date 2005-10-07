/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import jetbrains.fabrique.model.FProject;
import jetbrains.fabrique.util.ui.IdeaIcons;

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

  public FolderNode(FProject aProject) {
    this(aProject, null);
  }

  public FolderNode(FProject aProject, NodeDescriptor parent) {
    super(aProject, parent);
    myName = "";
    myFQName = "";
    init();
  }

  private void init() {
    setPlainText(myName);
    setIcons(IdeaIcons.FOLDER_ICON, IdeaIcons.FOLDER_OPEN_ICON);
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
