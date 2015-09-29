package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ToggleWholeWordsOnlyAction extends EditorHeaderToggleAction {
  public ToggleWholeWordsOnlyAction() {
    super("Wo&rds");
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    EditorSearchSession session = e.getData(EditorSearchSession.SESSION_KEY);
    e.getPresentation().setEnabled(session != null && !session.getFindModel().isRegularExpressions());
    e.getPresentation().setVisible(session != null && !session.getFindModel().isMultiline());
  }

  @Override
  protected boolean isSelected(@NotNull EditorSearchSession session) {
    return session.getFindModel().isWholeWordsOnly();
  }

  @Override
  protected void setSelected(@NotNull EditorSearchSession session, boolean selected) {
    FindSettings.getInstance().setLocalWholeWordsOnly(selected);
    session.getFindModel().setWholeWordsOnly(selected);
  }
}
