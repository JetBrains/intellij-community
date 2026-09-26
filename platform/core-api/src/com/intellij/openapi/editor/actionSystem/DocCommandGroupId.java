// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

public final class DocCommandGroupId {
  private final Document myDocument;
  private final Object myGroupId;

  public static @NotNull DocCommandGroupId noneGroupId(@NotNull Document doc) {
    return new DocCommandGroupId(doc, new Object());
  }

  public static @NotNull DocCommandGroupId withGroupId(@NotNull Document doc, @NotNull Object groupId) {
    return new DocCommandGroupId(doc, groupId);
  }

  private DocCommandGroupId(@NotNull Document document, @NotNull Object groupId) {
    myDocument = document;
    myGroupId = groupId;
  }

  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DocCommandGroupId that = (DocCommandGroupId)o;

    if (!myDocument.equals(that.myDocument)) return false;
    if (!myGroupId.equals(that.myGroupId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDocument.hashCode();
    result = 31 * result + myGroupId.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "DocCommandGroupId{" + myDocument +
           ", " + myGroupId +
           '}';
  }
}
