/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Document;

import java.util.EventObject;

public abstract class DocumentEvent extends EventObject {
  protected DocumentEvent(Document document) {
    super(document);
  }

  public abstract Document getDocument();

  public abstract int getOffset();

  public abstract int getOldLength();
  public abstract int getNewLength();

  public abstract CharSequence getOldFragment();
  public abstract CharSequence getNewFragment();

  public abstract long getOldTimeStamp();

}
