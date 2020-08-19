/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.util.breadcrumbs.BreadcrumbsPlacement;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.InvisibleWrapper;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

class DiffContentPanel extends JPanel {
  @Nullable private DiffBreadcrumbsPanel myBreadcrumbs;

  private final Wrapper myTitle = new InvisibleWrapper();
  private final Wrapper myTopBreadcrumbs = new InvisibleWrapper();
  private final JComponent myContent;
  private final Wrapper myBottomBreadcrumbs = new InvisibleWrapper();

  DiffContentPanel(@NotNull JComponent content) {
    myContent = content;

    initLayout(this, myTitle, myTopBreadcrumbs, myContent, myBottomBreadcrumbs);
  }

  public void setTitle(@Nullable JComponent titles) {
    myTitle.setContent(titles);
  }

  public void setBreadcrumbs(@Nullable DiffBreadcrumbsPanel breadcrumbs) {
    if (breadcrumbs != null) {
      myBreadcrumbs = breadcrumbs;
    }
  }

  public void updateBreadcrumbsPlacement(@NotNull BreadcrumbsPlacement placement) {
    if (myBreadcrumbs == null) return;

    myTopBreadcrumbs.setContent(placement == BreadcrumbsPlacement.TOP ? myBreadcrumbs : null);
    myBottomBreadcrumbs.setContent(placement == BreadcrumbsPlacement.BOTTOM ? myBreadcrumbs : null);
    myBreadcrumbs.setCrumbsShown(placement != BreadcrumbsPlacement.HIDDEN);

    validate();
    repaint();
  }

  private static void initLayout(@NotNull DiffContentPanel contentPanel,
                                 @NotNull JComponent title,
                                 @NotNull JComponent topBreadcrumbs,
                                 @NotNull JComponent content,
                                 @NotNull JComponent bottomBreadcrumbs) {
    contentPanel.removeAll();

    MigLayout mgr = new MigLayout(new LC().flowY().fill().hideMode(3)
                                    .insets("0").gridGapY("0"));
    contentPanel.setLayout(mgr);

    contentPanel.add(title, new CC().growX().minWidth("0").gapY("0", String.valueOf(DiffUtil.TITLE_GAP)));
    contentPanel.add(topBreadcrumbs, new CC().growX().minWidth("0"));
    contentPanel.add(content, new CC().grow().push());
    contentPanel.add(bottomBreadcrumbs, new CC().growX().minWidth("0"));
  }

  public static void syncTitleHeights(@NotNull List<DiffContentPanel> panels) {
    List<JComponent> titles = ContainerUtil.map(panels, it -> it.myTitle);
    List<JComponent> topBreadcrumbs = ContainerUtil.map(panels, it -> it.myTopBreadcrumbs);

    List<JComponent> syncTitles = DiffUtil.createSyncHeightComponents(titles);
    List<JComponent> syncTopBreadcrumbs = DiffUtil.createSyncHeightComponents(topBreadcrumbs);

    for (int i = 0; i < panels.size(); i++) {
      DiffContentPanel contentPanel = panels.get(i);
      JComponent title = syncTitles.get(i);
      JComponent topBreadcrumb = syncTopBreadcrumbs.get(i);
      initLayout(contentPanel, title, topBreadcrumb, contentPanel.myContent, contentPanel.myBottomBreadcrumbs);
    }
  }
}
