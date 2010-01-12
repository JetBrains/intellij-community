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

package com.intellij.find.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.find.FindBundle;
import com.intellij.lang.findUsages.EmptyFindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;

public class FindUsagesInFileAction extends AnAction {

  public FindUsagesInFileAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);

    UsageTarget[] usageTargets = UsageView.USAGE_TARGETS_KEY.getData(dataContext);
    if (usageTargets != null) {
      FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
      if (fileEditor != null) {
        usageTargets[0].findUsagesInEditor(fileEditor);
      }
    }
    else if (editor == null) {
      Messages.showMessageDialog(
        project,
        FindBundle.message("find.no.usages.at.cursor.error"),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
    else {
      HintManager.getInstance().showErrorHint(editor, FindBundle.message("find.no.usages.at.cursor.error"));
    }
  }

  public void update(AnActionEvent event){
    updateFindUsagesAction(event);
  }

  private static boolean isEnabled(DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      UsageTarget[] target = UsageView.USAGE_TARGETS_KEY.getData(dataContext);
      return target != null && target.length > 0;
    }
    else {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

      return file != null && !(LanguageFindUsages.INSTANCE.forLanguage(file.getLanguage()) instanceof EmptyFindUsagesProvider);
    }
  }

  public static void updateFindUsagesAction(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    boolean enabled = isEnabled(dataContext);
    presentation.setVisible(ActionPlaces.isPopupPlace(event.getPlace()) ? enabled : true);
    presentation.setEnabled(enabled);
  }
}
