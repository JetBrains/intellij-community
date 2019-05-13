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
package com.intellij.refactoring.move;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.ui.RefactoringDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class MoveDialogBase extends RefactoringDialog {

  private JCheckBox myOpenEditorCb;

  protected abstract String getMovePropertySuffix();
  protected abstract String getCbTitle();
  
  protected JCheckBox initOpenInEditorCb() {
    myOpenEditorCb = new JCheckBox(getCbTitle(), PropertiesComponent.getInstance().getBoolean("Move" + getMovePropertySuffix() +".OpenInEditor",
                                                                                              isEnabledByDefault()));
    return myOpenEditorCb;
  }

  protected boolean isEnabledByDefault() {
    return true;
  }

  protected void saveOpenInEditorOption() {
    if (myOpenEditorCb != null) {
      PropertiesComponent.getInstance().setValue("Move" + getMovePropertySuffix() +".OpenInEditor", myOpenEditorCb.isSelected(), isEnabledByDefault());
    }
  }

  protected boolean isOpenInEditor() {
    return myOpenEditorCb != null && myOpenEditorCb.isSelected();
  }

  protected MoveDialogBase(@NotNull Project project, boolean canBeParent) {
    super(project, canBeParent);
  }
}
