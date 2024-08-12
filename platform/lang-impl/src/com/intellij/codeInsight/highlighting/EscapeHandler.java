// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.highlighting.HighlightManager.HIDE_BY_ANY_KEY;
import static com.intellij.codeInsight.highlighting.HighlightManager.HIDE_BY_ESCAPE;

public final class EscapeHandler extends EditorActionHandler {
  private final @NotNull EditorActionHandler myOriginalHandler;

  public EscapeHandler(@NotNull EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  protected void doExecute(@NotNull Editor editor, Caret caret, DataContext dataContext){
    if (editor.getCaretModel().getCaretCount() == 1) {
      // Search results highlighting is disabled when the search popup is hidden from the screen,
      // but in tests it is not working, so we need to disable it manually.
      var realEditor = editor instanceof EditorWindow window ? window.getDelegate() : editor;
      var searchSession = EditorSearchSession.get(realEditor);
      if (searchSession != null) {
        searchSession.disableLivePreview();
      }
      editor.setHeaderComponent(null);

      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        HighlightManagerImpl highlightManager = (HighlightManagerImpl)HighlightManager.getInstance(project);
        if (highlightManager != null && highlightManager.hideHighlights(editor, HIDE_BY_ESCAPE | HIDE_BY_ANY_KEY)) {
          StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
          if (statusBar != null) {
            statusBar.setInfo("");
          }
          FindManager findManager = FindManager.getInstance(project);
          if (findManager != null) {
            FindModel model = findManager.getFindNextModel(editor);
            if (model != null) {
              model.setSearchHighlighters(false);
              findManager.setFindNextModel(model);
            }
          }
          return;
        }
      }
    }

    myOriginalHandler.execute(editor, caret, dataContext);
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (editor.hasHeaderComponent()) return true;
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (project != null) {
      HighlightManagerImpl highlightManager = (HighlightManagerImpl)HighlightManager.getInstance(project);
      if (highlightManager != null && highlightManager.hasHighlightersToHide(editor, HIDE_BY_ESCAPE | HIDE_BY_ANY_KEY)) {
        return true;
      }
      // Escape can be used to get rid of light bulb
      if (DaemonCodeAnalyzerEx.getInstanceEx(project).hasVisibleLightBulbOrPopup()) return true;
    }

    return myOriginalHandler.isEnabled(editor, caret, dataContext);
  }
}
