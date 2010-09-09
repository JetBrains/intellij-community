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

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.changeSignature.ChangeSignatureGestureDetector;

import java.util.Map;

public class EscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EscapeHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    editor.setHeaderComponent(null);
    if (project != null) {
      if (((HighlightManagerImpl)HighlightManager.getInstance(project)).hideHighlights(editor, HighlightManager.HIDE_BY_ESCAPE |
                                                                                               HighlightManager.HIDE_BY_ANY_KEY)) {
        WindowManager.getInstance().getStatusBar(project).setInfo(""); //??
        FindManager findManager = FindManager.getInstance(project);
        FindModel model = findManager.getFindNextModel(editor);
        if (model != null) {
          model.setSearchHighlighters(false);
          findManager.setFindNextModel(model);
        }
        return;
      }
      else {
        final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
        final ChangeSignatureGestureDetector detector = ChangeSignatureGestureDetector.getInstance(project);
        if (file != null && detector.containsChangeSignatureChange(file)) {
          detector.clearSignatureChange(file);
          DaemonCodeAnalyzer.getInstance(project).restart();
          return;
        }
      }
    }
    myOriginalHandler.execute(editor, dataContext);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    if (editor.hasHeaderComponent()) return true;
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    if (project != null) {
      HighlightManagerImpl highlightManager = (HighlightManagerImpl)HighlightManager.getInstance(project);
      Map<RangeHighlighter, HighlightManagerImpl.HighlightInfo> map = highlightManager.getHighlightInfoMap(editor, false);

      if (map != null) {
        for (HighlightManagerImpl.HighlightInfo info : map.values()) {
          if (!info.editor.equals(editor)) continue;
          if ((info.flags & HighlightManager.HIDE_BY_ESCAPE) != 0) {
            return true;
          }
        }
      }
      final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
      if (file != null && ChangeSignatureGestureDetector.getInstance(project).containsChangeSignatureChange(file)) {
        return true;
      }
    }

    return myOriginalHandler.isEnabled(editor, dataContext);
  }
}
