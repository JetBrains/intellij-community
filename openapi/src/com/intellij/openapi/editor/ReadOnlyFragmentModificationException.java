/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.DocumentEvent;

public class ReadOnlyFragmentModificationException extends RuntimeException {
  private DocumentEvent myIllegalAttemptEvent;
  private RangeMarker myGuardedBlock;

  public ReadOnlyFragmentModificationException(DocumentEvent illegalAttemptEvent, RangeMarker guardedBlock) {
    super("Attempt to modify read-only fragment");
    myIllegalAttemptEvent = illegalAttemptEvent;
    myGuardedBlock = guardedBlock;
  }

  public RangeMarker getGuardedBlock() {
    return myGuardedBlock;
  }

  public DocumentEvent getIllegalAttemptEvent() {
    return myIllegalAttemptEvent;
  }
}