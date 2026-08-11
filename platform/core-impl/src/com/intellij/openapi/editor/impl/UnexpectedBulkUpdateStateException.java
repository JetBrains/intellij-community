// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class UnexpectedBulkUpdateStateException extends RuntimeException implements ExceptionWithAttachments {
  private final Attachment[] myAttachments;

  UnexpectedBulkUpdateStateException(@Nullable Throwable enteringTrace) {
    super("Current operation is not permitted in bulk mode, see Document.isInBulkUpdate() javadoc");
    myAttachments = enteringTrace == null
                    ? Attachment.EMPTY_ARRAY
                    : new Attachment[] {new Attachment("enteringTrace.txt", enteringTrace)};
  }

  @Override
  public Attachment @NotNull [] getAttachments() {
    return myAttachments;
  }
}
