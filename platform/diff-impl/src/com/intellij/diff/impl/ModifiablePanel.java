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
package com.intellij.diff.impl;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ModifiablePanel extends JPanel {
  @Nullable private JComponent myContent;

  public ModifiablePanel() {
    super(new BorderLayout());
  }

  public void setContent(@Nullable JComponent content) {
    myContent = content;
    removeAll();
    if (content != null) add(content, BorderLayout.CENTER);
    invalidate();
  }

  @Nullable
  public JComponent getContent() {
    return myContent;
  }
}
