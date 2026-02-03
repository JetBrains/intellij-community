// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.LanguageCallHierarchy;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class BrowseCallHierarchyAction extends BrowseHierarchyActionBase {
  public BrowseCallHierarchyAction() {
    super(LanguageCallHierarchy.INSTANCE);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    if (!ActionPlaces.isMainMenuOrActionSearch(event.getPlace())) {
      presentation.setText(IdeBundle.messagePointer("action.browse.call.hierarchy"));
    }

    super.update(event);
  }
}