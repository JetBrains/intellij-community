package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.util.ui.EmptyIcon;

public abstract class EditorHeaderToggleAction extends ToggleAction {

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (e.getPresentation().getIcon() == null && !isSecondary()) {
      e.getPresentation().setIcon(new EmptyIcon(1, 1));
    }
  }

  private boolean isSecondary() {
    return this instanceof SecondaryHeaderAction;
  }

  private EditorSearchComponent myEditorSearchComponent;

  protected EditorHeaderToggleAction(EditorSearchComponent editorSearchComponent, String text, String mnemonic) {
    super(text);
    myEditorSearchComponent = editorSearchComponent;
  }
}
