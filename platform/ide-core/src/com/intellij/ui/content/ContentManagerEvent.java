// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

public class ContentManagerEvent extends EventObject {
  private final @NotNull Content myContent;
  private final int myIndex;
  private boolean myConsumed;
  private final ContentOperation myOperation;

  public ContentManagerEvent(Object source, @NotNull Content content, int index, ContentOperation operation) {
    super(source);
    myContent = content;
    myIndex = index;
    myOperation = operation;
  }

  public ContentManagerEvent(Object source, @NotNull Content content, int index) {
    this(source, content, index, ContentOperation.undefined);
  }

  public @NotNull Content getContent() {
    return myContent;
  }

  public int getIndex() {
    return myIndex;
  }

  public boolean isConsumed() {
    return myConsumed;
  }

  public void consume() {
    myConsumed = true;
  }

  public ContentOperation getOperation() {
    return myOperation;
  }

  public enum ContentOperation {
    add, remove, undefined
  }
}
