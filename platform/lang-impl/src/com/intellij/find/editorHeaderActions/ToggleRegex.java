/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.find.FindSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleRegex extends EditorHeaderToggleAction {

  private static final String REGEX = "R&egex";

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getEditorSearchComponent().getFindModel().isRegularExpressions();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final FindModel findModel = getEditorSearchComponent().getFindModel();
    findModel.setRegularExpressions(state);
    if (state) {
      findModel.setWholeWordsOnly(false);
    }
    FindSettings.getInstance().setLocalRegularExpressions(state);
  }

  public ToggleRegex(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, REGEX);
  }
}
