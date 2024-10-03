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

import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public class OnesideContentPanel extends JPanel {
  private final DiffContentPanel myPanel;

  public OnesideContentPanel(@NotNull JComponent content) {
    super(new BorderLayout());

    myPanel = new DiffContentPanel(content);
    add(myPanel, BorderLayout.CENTER);
  }

  public void setTitle(@Nullable JComponent titles) {
    myPanel.setTitle(titles);
  }

  public void setBreadcrumbs(@Nullable DiffBreadcrumbsPanel breadcrumbs, @NotNull TextDiffSettings settings) {
    if (breadcrumbs != null) {
      myPanel.setBreadcrumbs(breadcrumbs);
      myPanel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
      settings.addListener(new TextDiffSettings.Listener.Adapter() {
        @Override
        public void breadcrumbsPlacementChanged() {
          myPanel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
        }
      }, breadcrumbs);
    }
  }

  @NotNull
  public static OnesideContentPanel createFromHolder(@NotNull EditorHolder holder) {
    return new OnesideContentPanel(holder.getComponent());
  }
}
