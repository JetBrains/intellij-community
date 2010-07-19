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

package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.DumbAware;

public class FoldingActionGroup extends DefaultActionGroup  implements DumbAware {
  public FoldingActionGroup() {
    super();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null){
      presentation.setVisible(false);
      return;
    }

    FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
    presentation.setVisible(foldingModel.isFoldingEnabled());
  }
}