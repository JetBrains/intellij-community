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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class UnifiedContentPanel extends JPanel {
  public UnifiedContentPanel(@NotNull List<JComponent> titles, @NotNull Editor editor) {
    super(new BorderLayout());
    assert titles.size() == 2;

    add(editor.getComponent(), BorderLayout.CENTER);

    titles = ContainerUtil.skipNulls(titles);
    if (!titles.isEmpty()) {
      add(DiffUtil.createStackedComponents(titles, DiffUtil.TITLE_GAP), BorderLayout.NORTH);
    }
  }
}
