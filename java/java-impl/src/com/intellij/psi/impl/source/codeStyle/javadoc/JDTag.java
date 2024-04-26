// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum JDTag {
  SEE("see"),
  AUTHOR("author"),
  VERSION("version"),
  THROWS("throws"),
  EXCEPTION("exception"),
  RETURN("return"),
  PARAM("param"),
  SINCE("since"),
  DEPRECATED("deprecated");


  private final @NotNull String myTag;

  JDTag(@NotNull String tag) {
    this.myTag = tag;
  }

  public @NotNull String getWithEndWhitespace() {
    return "@" + myTag + " ";
  }

  public boolean tagEqual(@Nullable String tag) {
    return myTag.equals(tag);
  }
}
