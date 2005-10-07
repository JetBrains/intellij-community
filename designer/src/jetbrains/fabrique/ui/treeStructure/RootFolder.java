/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import jetbrains.fabrique.model.FProject;

/**
 * @author kir
 */
public class RootFolder extends FolderNode {

  public RootFolder(FProject aProject) {
    super(aProject, null);
  }

  public boolean isAutoExpandNode() {
    return true;
  }
}
