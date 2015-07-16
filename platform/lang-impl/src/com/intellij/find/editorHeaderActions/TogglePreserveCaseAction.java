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
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class TogglePreserveCaseAction extends EditorHeaderToggleAction {
  private static final String TEXT = "Pr&eserve Case";

  public TogglePreserveCaseAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, TEXT);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myEditorSearchComponent.getFindModel().isPreserveCase();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    FindModel findModel = myEditorSearchComponent.getFindModel();
    e.getPresentation().setEnabled(!findModel.isRegularExpressions());
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myEditorSearchComponent.getFindModel().setPreserveCase(state);
  }
}
