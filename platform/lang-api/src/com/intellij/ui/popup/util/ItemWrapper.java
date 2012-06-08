/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

public interface ItemWrapper {
  void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected);

  void setupRenderer(ColoredTreeCellRenderer renderer);

  void updateAccessoryView(JComponent label);

  String speedSearchText();

  @Nullable
  String footerText();

  void updateDetailView(DetailView panel);

  boolean allowedToRemove();

  void removed(Project project);
}
