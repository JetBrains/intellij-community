package com.intellij.find.editorHeaderActions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindModel;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SwitchToReplace extends DumbAwareAction implements LightEditCompatible {
  public SwitchToReplace(@NotNull JComponent shortcutHolder) {
    AnAction replaceAction = ActionManager.getInstance().getAction(IdeActions.ACTION_REPLACE);
    if (replaceAction != null) {
      registerCustomShortcutSet(replaceAction.getShortcutSet(), shortcutHolder);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);
    Editor editor = search != null ? search.getEditor() : e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    e.getPresentation().setEnabled(editor != null && search != null && !ConsoleViewUtil.isConsoleViewEditor(editor));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    EditorSearchSession search = e.getRequiredData(EditorSearchSession.SESSION_KEY);
    FindModel findModel = search.getFindModel();
    if (!findModel.isReplaceState()) {
      findModel.setReplaceState(true);
    }
    search.getComponent().getSearchTextComponent().selectAll();
  }
}
