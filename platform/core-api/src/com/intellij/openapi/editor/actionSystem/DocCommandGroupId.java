// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.editor.Document;

public final class DocCommandGroupId {
  private final Document myDocument;
  private final Object myGroupId;

  public static DocCommandGroupId noneGroupId(Document doc) {
    return new DocCommandGroupId(doc, new Object());
  }

  public static DocCommandGroupId withGroupId(Document doc, Object groupId) {
    return new DocCommandGroupId(doc, groupId);
  }

  private DocCommandGroupId(Document document, Object groupId) {
    myDocument = document;
    myGroupId = groupId;
  }

  public Document getDocument() {
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
}
