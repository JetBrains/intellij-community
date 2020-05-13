// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

public class MockDocumentEvent extends DocumentEvent {
  private final int myOffset;
  private final long myTimestamp;

  public MockDocumentEvent(@NotNull Document document, int offset) {
    super(document);
    myOffset = offset;
    myTimestamp = document.getModificationStamp();
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public int getOldLength() {
    return 0;
  }

  @Override
  public int getNewLength() {
    return 0;
  }

  @Override
  @NotNull
  public CharSequence getOldFragment() {
    return "";
  }

  @Override
  @NotNull
  public CharSequence getNewFragment() {
    return "";
  }

  @Override
  public long getOldTimeStamp() {
    return myTimestamp;
  }
}
