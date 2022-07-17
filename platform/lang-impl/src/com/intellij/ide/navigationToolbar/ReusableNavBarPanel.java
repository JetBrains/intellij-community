// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * NavBarPanel inheritor which could be reused in different view structures.
 * To save resources the original NavBarPanel disposes them in removeNotify().
 * ReusableNavBarPanel doesn't dispose resources on removal. That allows adding/ing the panel to/from the panes structure multiple times.
 */
public class ReusableNavBarPanel extends NavBarPanel {
  public ReusableNavBarPanel(@NotNull Project project, boolean docked) {
    super(project, docked);
  }

  @Override
  protected boolean isDisposeOnRemove() {
    return false;
  }
}
