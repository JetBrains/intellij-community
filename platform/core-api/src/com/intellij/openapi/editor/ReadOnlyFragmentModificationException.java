// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NonNls;

public class ReadOnlyFragmentModificationException extends RuntimeException {
  private final DocumentEvent myIllegalAttemptEvent;
  private final RangeMarker myGuardedBlock;
  public static final @NonNls String MESSAGE = "Attempt to modify read-only fragment";

  public ReadOnlyFragmentModificationException(DocumentEvent illegalAttemptEvent, RangeMarker guardedBlock) {
    super(MESSAGE);
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
