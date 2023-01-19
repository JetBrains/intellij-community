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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.packaging.elements.PackagingElementType;
import org.jetbrains.annotations.NotNull;

public class AddNewPackagingElementAction extends DumbAwareAction {
  private final PackagingElementType<?> myType;
  private final ArtifactEditorEx myArtifactEditor;

  public AddNewPackagingElementAction(PackagingElementType<?> type, ArtifactEditorEx artifactEditor) {
    super(type.getPresentableName(), null, type.getCreateElementIcon());
    myType = type;
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(myType.canCreate(myArtifactEditor.getContext(), myArtifactEditor.getArtifact()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myArtifactEditor.addNewPackagingElement(myType);
  }
}
