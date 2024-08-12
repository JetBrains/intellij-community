// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.favoritesTreeView;

import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = true)
public class NoteNode {

  private @NotNull String myText;
  private final boolean myReadonly;

  public NoteNode(@NotNull String text, boolean readonly) {
    myText = text;
    myReadonly = readonly;
  }

  public @NotNull String getText() {
    return myText;
  }

  public void setText(@NotNull String text) {
    myText = text;
  }

  public boolean isReadonly() {
    return myReadonly;
  }
}
