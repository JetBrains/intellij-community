// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface SerializedInsertHandler {
  // This looks over-engineered, though we'll eventually come tox Builders when widely adopting this approach.
  @NotNull Map<Character, InsertionPayloadBuilder> snippetPerCompletionChar();

  interface InsertionPayload {
    @NotNull String snippetToInsert();
    // How many symbols after caret should be deleted before inserting the snippet
    @Nullable Integer deleteAfterCursor();
    @NotNull Boolean shouldCallPostCompletion();
  }

  interface InsertionPayloadBuilder {
    @NotNull InsertionPayload build();
  }
}
