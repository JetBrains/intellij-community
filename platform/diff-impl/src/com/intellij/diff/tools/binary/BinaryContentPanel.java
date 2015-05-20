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
package com.intellij.diff.tools.binary;

import com.intellij.diff.tools.util.twoside.TwosideContentPanel;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class BinaryContentPanel extends TwosideContentPanel {
  public BinaryContentPanel(@NotNull List<JComponent> titleComponents,
                            @Nullable FileEditor editor1,
                            @Nullable FileEditor editor2) {
    super(titleComponents, getComponent(editor1), getComponent(editor2));
  }

  @Nullable
  private static JComponent getComponent(@Nullable FileEditor editor) {
    return editor != null ? editor.getComponent() : null;
  }
}
