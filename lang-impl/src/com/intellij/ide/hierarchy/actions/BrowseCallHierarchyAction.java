package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.LanguageCallHierarchy;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

public final class BrowseCallHierarchyAction extends BrowseHierarchyActionBase {
  public BrowseCallHierarchyAction() {
    super(LanguageCallHierarchy.INSTANCE);
  }

  public final void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.browse.call.hierarchy"));
    }

    super.update(event);
  }
}