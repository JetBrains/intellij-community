/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.project.Project;

import java.awt.*;

/**
 * @deprecated
 */
public class DiffPanelFactory {

  /**
   * @deprecated
   */
  public static DiffPanel createDiffPanel(Project project, boolean enableToolbar) {
    return createDiffPanel(project, null, enableToolbar);
  }

  /**
   * @deprecated 
   * @param ownerWindow this window will be disposed, when user clicks on the line number
   */
  public static DiffPanel createDiffPanel(Project project, Window ownerWindow, boolean enableToolbar) {
    return DiffManager.getInstance().createDiffPanel(ownerWindow, project);
  }
}
