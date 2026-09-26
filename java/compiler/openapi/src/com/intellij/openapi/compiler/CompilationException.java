// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class CompilationException extends Exception {
  private final Collection<? extends Message> myMessages;

  public static class Message {
    private final @NotNull CompilerMessageCategory myCategory;
    private final @NotNull @Nls String myMessage;
    private final @Nullable String myUrl;
    private final int myLine;
    private final int myColumn;

    public Message(@NotNull CompilerMessageCategory category, @NotNull @Nls String message, @Nullable String url, int line, int column) {
      myCategory = category;
      myMessage = message;
      myUrl = url;
      myLine = line;
      myColumn = column;
    }

    public @NotNull CompilerMessageCategory getCategory() {
      return myCategory;
    }

    public @NotNull @Nls String getText() {
      return myMessage;
    }

    public @Nullable String getUrl() {
      return myUrl;
    }

    public int getLine() {
      return myLine;
    }

    public int getColumn() {
      return myColumn;
    }
  }

  public CompilationException(@NotNull String message, @NotNull Collection<? extends Message> messages) {
    super(message);
    myMessages = messages;
  }

  public @NotNull Collection<? extends Message> getMessages() {
    return myMessages;
  }
}
