// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public final class ActionOnSaveComment {
  private final @NotNull @NlsContexts.Label String myCommentText;
  private final boolean myIsWarning;

  private ActionOnSaveComment(@NotNull @NlsContexts.Label String commentText, boolean isWarning) {
    myCommentText = commentText;
    myIsWarning = isWarning;
  }

  public @NotNull @NlsContexts.Label String getCommentText() {
    return myCommentText;
  }

  public boolean isWarning() {
    return myIsWarning;
  }

  public static ActionOnSaveComment info(@NotNull @NlsContexts.Label String commentText) {
    return new ActionOnSaveComment(commentText, false);
  }

  public static ActionOnSaveComment warning(@NotNull @NlsContexts.Label String commentText) {
    return new ActionOnSaveComment(commentText, true);
  }
}
