package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;

import java.util.Map;

public class EscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EscapeHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    editor.setHeaderComponent(null);
    if (project != null && ((HighlightManagerImpl)HighlightManager.getInstance(project)).hideHighlights(editor, HighlightManager.HIDE_BY_ESCAPE |
                                                                                                                HighlightManager .HIDE_BY_ANY_KEY)) {
      WindowManager.getInstance().getStatusBar(project).setInfo(""); //??
      FindManager findManager = FindManager.getInstance(project);
      FindModel model = findManager.getFindNextModel(editor);
      if (model != null) {
        model.setSearchHighlighters(false);
        findManager.setFindNextModel(model);
      }
    }
    else{
      myOriginalHandler.execute(editor, dataContext);
    }
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
    }

    return myOriginalHandler.isEnabled(editor, dataContext);
  }
}
