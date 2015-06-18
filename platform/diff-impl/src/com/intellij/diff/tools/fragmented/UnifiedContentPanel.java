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

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class UnifiedContentPanel extends JPanel {
  public UnifiedContentPanel(@NotNull List<JComponent> titles, @NotNull Editor editor) {
    super(new BorderLayout());

    add(editor.getComponent(), BorderLayout.CENTER);

    assert titles.size() == 2;
    JComponent title1 = titles.get(0);
    JComponent title2 = titles.get(1);

    if (title1 != null || title2 != null) {
      if (title1 == null) {
        add(title2, BorderLayout.NORTH);
      }
      else if (title2 == null) {
        add(title1, BorderLayout.NORTH);
      }
      else {
        JPanel panel = new JPanel();
        BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(layout);

        panel.add(title2);
        panel.add(title1);
        add(panel, BorderLayout.NORTH);
      }
    }
  }
}
