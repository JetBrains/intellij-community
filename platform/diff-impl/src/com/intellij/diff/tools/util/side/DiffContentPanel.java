// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.util.breadcrumbs.BreadcrumbsPlacement;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.IslandsState;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

@ApiStatus.Internal
public class DiffContentPanel extends DiffContentLayoutPanel {
  private @Nullable DiffBreadcrumbsPanel myBreadcrumbs;

  public DiffContentPanel(@NotNull JComponent content) {
    super(content);

    // setting this here instead of superclass to avoid double borders in remdev
    //TODO: make dynamic
    if (IslandsState.Companion.isEnabled()) {
      var lineColor = ObjectUtils.notNull(
        EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.TEARLINE_COLOR),
        JBColor.border()
      );
      var border = JBUI.Borders.compound(
        JBUI.Borders.customLineBottom(lineColor),
        JBUI.Borders.empty(DiffUtil.getContentTitleBorderInsets())
      );
      myTitle.setBorder(border);
    }
    else {
      myTitle.setBorder(JBUI.Borders.empty(DiffUtil.getContentTitleBorderInsets()));
    }
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
