/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push;

import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class TargetEditor<T extends PushTarget> extends JPanel {

  protected TargetEditor(BorderLayout layout) {
    super(layout);
  }

  abstract public void render(@NotNull ColoredTreeCellRenderer renderer);

  @NotNull
  abstract public T getValue();

  public abstract void fireOnCancel();

  public abstract void fireOnChange();

  @Nullable
  public abstract VcsError verify();

  @NotNull
  public abstract JComponent getVerifiedComponent();
}
