package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.LanguageMethodHierarchy;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

public final class BrowseMethodHierarchyAction extends BrowseHierarchyActionBase {
  public BrowseMethodHierarchyAction() {
    super(LanguageMethodHierarchy.INSTANCE);
  }

  public final void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.browse.method.hierarchy"));
    }
    super.update(event);
  }
}