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
package com.intellij.ui.popup.util;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ItemWrapper {
  public abstract void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected);

  public abstract void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected);

  public abstract void updateAccessoryView(JComponent label);

  public abstract String speedSearchText();

  @Nullable
  public abstract String footerText();

  public void updateDetailView(DetailView panel) {
    if (panel != null) {
      doUpdateDetailView(panel, panel.hasEditorOnly());
      panel.setCurrentItem(this);
    }
  }

  protected abstract void doUpdateDetailView(DetailView panel, boolean editorOnly);

  public abstract boolean allowedToRemove();

  public abstract void removed(Project project);
}
