// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

public interface IndentedCommenter extends Commenter {
  /**
   * Used to override CodeStyleSettings#LINE_COMMENT_AT_FIRST_COLUMN option
   * @return true or false to override, null to use settings option
   */
  @Nullable
  Boolean forceIndentedLineComment();

  /**
   * Used to override CodeStyleSettings#BLOCK_COMMENT_AT_FIRST_COLUMN option
   * @return true or false to override, null to use settings option
   */
  default @Nullable Boolean forceIndentedBlockComment() {
    return null;
  }
}
