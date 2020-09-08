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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class LayoutTreeActionBase extends DumbAwareAction {
  protected final ArtifactEditorEx myArtifactEditor;

  protected LayoutTreeActionBase(@Nullable @NlsActions.ActionText String text,
                                 @Nullable @NlsActions.ActionDescription String description,
                                 @Nullable Icon icon,
                                 @NotNull ArtifactEditorEx artifactEditor) {
    super(text, description, icon);
    myArtifactEditor = artifactEditor;
  }

  protected LayoutTreeActionBase(String text, ArtifactEditorEx artifactEditor) {
    this(() -> text, artifactEditor);
  }

  protected LayoutTreeActionBase(@NotNull Supplier<String> text, ArtifactEditorEx artifactEditor) {
    super(text);
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled());
  }

  protected abstract boolean isEnabled();
}
