// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;

public final class ToggleInLiteralsOnlyAction extends EditorHeaderSetSearchContextAction {
  public ToggleInLiteralsOnlyAction() {
    super(FindBundle.message("search.context.title.in.string.literals"), FindModel.SearchContext.IN_STRING_LITERALS);
  }
}
