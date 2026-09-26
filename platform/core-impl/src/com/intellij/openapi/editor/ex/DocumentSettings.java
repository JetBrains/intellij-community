// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is responsible for storing document-wide flags like
 * whitespace stripping, line separator handling, and read-only checks.
 *
 * @see Document#setReadOnly
 * @see Document#setCyclicBufferSize
 * @see Document#startGuardedBlockChecking
 * @see DocumentEx#suppressGuardedExceptions
 * @see DocumentEx#setStripTrailingSpacesEnabled
 * @see com.intellij.openapi.editor.impl.DocumentImpl#setAcceptSlashR
 */
@ApiStatus.Internal
public interface DocumentSettings {

  @Contract(pure = true)
  boolean isWriteAccessCheckEnabled();

  @Contract(pure = true)
  boolean isCommandCheckEnabled();

  @Contract(pure = true)
  boolean isPCEWarningEnabled();

  @Contract(pure = true)
  boolean isSlashRAllowed();

  boolean setSlashRAllowed(boolean accept);

  void assertValidSeparators(@NotNull CharSequence charsToValidate);

  @Contract(pure = true)
  int cycleBufferSize();

  void setCycleBufferSize(int buffer);

  @Contract(pure = true)
  boolean isStripTrailingSpacesEnabled();

  void setStripTrailingSpaces(boolean strip);

  @Contract(pure = true)
  boolean isGuardCheckEnabled(boolean wholeTextReplaced);

  void startGuardCheck();

  void stopGuardCheck();

  void suppressGuardCheck(boolean onlyWholeText);

  void unsuppressGuardCheck(boolean onlyWholeText);

  boolean setReadOnly(boolean readOnly);

  boolean isWritable(@NotNull Document hostDocument);

  void assertWriteAccess(@NotNull Document hostDocument);

  void assertWritable(@NotNull Document hostDocument);

  void assertInsideCommand();

  @Contract(pure = true)
  @Nullable ReadonlyFragmentModificationHandler readOnlyHandler();

  void setReadOnlyHandler(@Nullable ReadonlyFragmentModificationHandler readonlyHandler);
}
