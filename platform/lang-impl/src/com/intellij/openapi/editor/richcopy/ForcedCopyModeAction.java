/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings;

public abstract class ForcedCopyModeAction extends AnAction {
  private final boolean myRichCopyEnabled;
  
  protected ForcedCopyModeAction(boolean richCopyEnabled) {
    myRichCopyEnabled = richCopyEnabled;
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation p = e.getPresentation();
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    p.setVisible(RichCopySettings.getInstance().isEnabled() != myRichCopyEnabled &&
                 (e.isFromActionToolbar() || (editor != null && editor.getSelectionModel().hasSelection(true))));
    p.setEnabled(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    RichCopySettings settings = RichCopySettings.getInstance();
    boolean savedValue = settings.isEnabled();
    try {
      settings.setEnabled(myRichCopyEnabled);
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_COPY).actionPerformed(e);
    }
    finally {
      settings.setEnabled(savedValue);
    }
  }
}
