/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Document;

public class MockDocumentEvent extends DocumentEvent {
  private int myOffset;
  private long myTimestamp;

  public MockDocumentEvent(Document document, int offset) {
    super(document);
    myOffset = offset;
    myTimestamp = document.getModificationStamp();
  }

  public Document getDocument() {
    return (Document)getSource();
  }

  public int getOffset() {
    return myOffset;
  }

  public int getOldLength() {
    return 0;
  }

  public int getNewLength() {
    return 0;
  }

  public CharSequence getOldFragment() {
    return "";
  }

  public CharSequence getNewFragment() {
    return "";
  }

  public long getOldTimeStamp() {
    return myTimestamp;
  }
}