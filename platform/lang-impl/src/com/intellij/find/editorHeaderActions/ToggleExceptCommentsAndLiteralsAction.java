// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;

public class ToggleExceptCommentsAndLiteralsAction extends EditorHeaderSetSearchContextAction {
  public ToggleExceptCommentsAndLiteralsAction() {
    super(
      FindBundle.message("search.context.title.except.comments.string.literals"),
      FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS
    );
  }
}
