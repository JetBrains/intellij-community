/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.packaging.ui.TreeNodePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ArtifactEditorNavigateActionBase extends DumbAwareAction {
  public ArtifactEditorNavigateActionBase(JComponent contextComponent) {
    super(JavaUiBundle.message("action.name.facet.navigate"));
    registerCustomShortcutSet(CommonShortcuts.getEditSource(), contextComponent);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final TreeNodePresentation presentation = getPresentation();
    e.getPresentation().setEnabled(presentation != null && presentation.canNavigateToSource());
  }

  @Nullable
  protected abstract TreeNodePresentation getPresentation();

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final TreeNodePresentation presentation = getPresentation();
    if (presentation != null) {
      presentation.navigateToSource();
    }
  }
}
