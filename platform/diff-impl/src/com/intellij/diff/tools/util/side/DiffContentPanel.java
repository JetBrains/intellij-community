// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.util.breadcrumbs.BreadcrumbsPlacement;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public class DiffContentPanel extends DiffContentLayoutPanel {
  private @Nullable DiffBreadcrumbsPanel myBreadcrumbs;

  public DiffContentPanel(@NotNull JComponent content) {
    super(content);
  }

  public void setBreadcrumbs(@Nullable DiffBreadcrumbsPanel breadcrumbs) {
    if (breadcrumbs != null) {
      myBreadcrumbs = breadcrumbs;
    }
  }

  public void updateBreadcrumbsPlacement(@NotNull BreadcrumbsPlacement placement) {
    if (myBreadcrumbs == null) return;

    setTopBreadcrumbs(placement == BreadcrumbsPlacement.TOP ? myBreadcrumbs : null);
    setBottomBreadcrumbs(placement == BreadcrumbsPlacement.BOTTOM ? myBreadcrumbs : null);
    myBreadcrumbs.setCrumbsShown(placement != BreadcrumbsPlacement.HIDDEN);

    validate();
    repaint();
  }
}
