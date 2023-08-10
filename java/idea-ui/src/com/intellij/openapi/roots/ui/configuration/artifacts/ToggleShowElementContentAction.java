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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import org.jetbrains.annotations.NotNull;

public class ToggleShowElementContentAction extends ToggleAction implements DumbAware {
  private final ComplexPackagingElementType<?> myType;
  private final ArtifactEditorImpl myEditor;

  public ToggleShowElementContentAction(ComplexPackagingElementType<?> type, ArtifactEditorImpl editor) {
    super(type.getShowContentActionText());
    myType = type;
    myEditor = editor;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myEditor.getSubstitutionParameters().isShowContentForType(myType);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    myEditor.getSubstitutionParameters().setShowContent(myType, state);
    myEditor.updateShowContentCheckbox();
    myEditor.getLayoutTreeComponent().rebuildTree();
  }
}
