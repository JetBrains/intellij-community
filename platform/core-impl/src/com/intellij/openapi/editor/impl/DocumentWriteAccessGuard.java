// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A last resort to guarantee that no activity is going to modify documents bypassing
 * {@link com.intellij.openapi.fileEditor.FileDocumentManager#requestWritingStatus FileDocumentManager.requestWritingStatus}
 * and similar API.
 * To forbid file modifications use {@link com.intellij.openapi.vfs.WritingAccessProvider WritingAccessProvider}
 */
@ApiStatus.Experimental
public abstract class DocumentWriteAccessGuard {

  public static final ExtensionPointName<DocumentWriteAccessGuard> EP_NAME =
    ExtensionPointName.create("com.intellij.documentWriteAccessGuard");

  public abstract @NotNull Result isWritable(@NotNull Document document);

  protected static Result success() {
    return new Result(true, null);
  }

  protected static Result fail(String failureReason) {
    return new Result(false, failureReason);
  }

  public static class Result {
    private final boolean mySuccess;
    private final String myFailureReason;

    private Result(boolean success, String failureReason) {
      mySuccess = success;
      myFailureReason = failureReason;
    }

    public boolean isSuccess() {
      return mySuccess;
    }

    public String getFailureReason() {
      return myFailureReason;
    }
  }
}
