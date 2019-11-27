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

import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import com.intellij.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class DiffContentPanel extends JPanel {
  DiffContentPanel(@NotNull JComponent content) {
    super(new BorderLayout(0, DiffUtil.TITLE_GAP));
    add(content, BorderLayout.CENTER);
  }

  public void setTitle(@Nullable JComponent titles) {
    if (titles != null) {
      add(titles, BorderLayout.NORTH);
    }
  }

  public void setBreadcrumbs(@Nullable DiffBreadcrumbsPanel breadcrumbs) {
    if (breadcrumbs != null) {
      add(breadcrumbs, BorderLayout.SOUTH);
    }
  }
}
