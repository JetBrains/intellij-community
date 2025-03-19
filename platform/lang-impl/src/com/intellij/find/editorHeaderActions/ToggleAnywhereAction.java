// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.KeepPopupOnPerform;

public final class ToggleAnywhereAction extends EditorHeaderSetSearchContextAction {
  public ToggleAnywhereAction() {
    super(FindBundle.message("search.context.title.anywhere"), FindModel.SearchContext.ANY);

    getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested);
  }
}
