/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import jetbrains.fabrique.model.FProject;
import jetbrains.fabrique.model.FProject;

public abstract class SimpleRoot extends SimpleNode {

  public SimpleRoot(FProject aProject) {
    super(aProject, null);
  }
}
