// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.find.impl.livePreview.SearchResults;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FindPrevWordAtCaretAction extends FindWordAtCaretAction {
  public FindPrevWordAtCaretAction() {
    super(new FindWordAtCaretAction.Handler(SearchResults.Direction.UP));
  }
}
