// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/**
 * JSON standard (RFC 4627) doesn't allow comments in documents, but they are added for compatibility with legacy JSON integration.
 *
 * @author Mikhail Golubev
 */
public class JsonCommenter implements Commenter {
  @Override
  public @Nullable String getLineCommentPrefix() {
    return "//";
  }

  @Override
  public @Nullable String getBlockCommentPrefix() {
    return "/*";
  }

  @Override
  public @Nullable String getBlockCommentSuffix() {
    return "*/";
  }

  @Override
  public @Nullable String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getCommentedBlockCommentSuffix() {
    return null;
  }
}
