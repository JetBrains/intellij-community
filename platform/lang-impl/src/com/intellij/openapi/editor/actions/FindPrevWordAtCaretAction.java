// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.find.impl.livePreview.SearchResults;

public class FindPrevWordAtCaretAction extends FindWordAtCaretAction {
  public FindPrevWordAtCaretAction() {
    super(new FindWordAtCaretAction.Handler(SearchResults.Direction.UP));
  }
}
