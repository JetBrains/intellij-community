// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;

public class ToggleExceptLiteralsAction extends EditorHeaderSetSearchContextAction {
  public ToggleExceptLiteralsAction() {
    super(FindBundle.message("search.context.title.except.string.literals"), FindModel.SearchContext.EXCEPT_STRING_LITERALS);
  }
}
