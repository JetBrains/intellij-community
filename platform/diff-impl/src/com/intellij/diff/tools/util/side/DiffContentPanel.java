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
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class DiffContentPanel extends JPanel {
  @Nullable private DiffBreadcrumbsPanel myBreadcrumbs;

  private final Wrapper myTitle = new Wrapper();
  private final Wrapper myTopBreadcrumbs = new InvisibleWrapper();
  private final Wrapper myBottomBreadcrumbs = new InvisibleWrapper();

  DiffContentPanel(@NotNull JComponent content) {
    MigLayout mgr = new MigLayout(new LC().flowY().fill().hideMode(3)
                                    .insets("0").gridGapY("0"));
    setLayout(mgr);

    add(myTitle, new CC().growX().minWidth("0").gapY("0", String.valueOf(DiffUtil.TITLE_GAP)));
    add(myTopBreadcrumbs, new CC().growX().minWidth("0"));
    add(content, new CC().grow().push());
    add(myBottomBreadcrumbs, new CC().growX().minWidth("0"));
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
}
