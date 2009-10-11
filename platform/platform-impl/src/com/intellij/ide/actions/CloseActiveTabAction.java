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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;

public class CloseActiveTabAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(e.getDataContext(), true);
    if (contentManager != null && contentManager.canCloseContents()) {
      final Content selectedContent = contentManager.getSelectedContent();
      if (selectedContent != null && selectedContent.isCloseable()) {
        contentManager.removeContent(selectedContent, true);
      }
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    ContentManager contentManager=ContentManagerUtil.getContentManagerFromContext(event.getDataContext(), true);
    presentation.setEnabled(contentManager != null && contentManager.canCloseContents());
  }
}