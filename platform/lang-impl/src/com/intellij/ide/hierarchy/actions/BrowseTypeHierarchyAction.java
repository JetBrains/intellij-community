package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.LanguageTypeHierarchy;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

public final class BrowseTypeHierarchyAction extends BrowseHierarchyActionBase {
  public BrowseTypeHierarchyAction() {
    super(LanguageTypeHierarchy.INSTANCE);
  }

  public final void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.browse.type.hierarchy"));
    }
    super.update(event);
  }
}