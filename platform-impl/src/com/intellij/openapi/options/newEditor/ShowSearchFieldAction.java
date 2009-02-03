package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.IconLoader;

public class ShowSearchFieldAction extends AnAction implements Toggleable {

  private final ShadowAction myShadow;
  private final OptionsEditor myEditor;


  public ShowSearchFieldAction(OptionsEditor editor) {
    myEditor = editor;
    myShadow = new ShadowAction(this, ActionManager.getInstance().getAction("Find"), editor);
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setIcon(IconLoader.getIcon("/actions/find.png"));
    e.getPresentation().putClientProperty(SELECTED_PROPERTY, myEditor.isFilterFieldVisible());
  }

  public void actionPerformed(final AnActionEvent e) {
    if (myEditor.getContext().isHoldingFilter()) {
      myEditor.setFilterFieldVisible(true, true, true);
    } else {
      myEditor.setFilterFieldVisible(!myEditor.isFilterFieldVisible(), true, true);
    }
  }
}
