// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.KeepPopupOnPerform;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ToggleInCommentsAction extends EditorHeaderSetSearchContextAction {
  public ToggleInCommentsAction() {
    super(FindBundle.message("search.context.title.in.comments"), FindModel.SearchContext.IN_COMMENTS);

    getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested);
  }
}
