// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class ThreadingAssertions {

  private ThreadingAssertions() { }

  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ThreadingAssertions.class);
  }

  @Internal
  public static final String MUST_EXECUTE_INSIDE_READ_ACTION =
    "Read access is allowed from inside read-action or Event Dispatch Thread (EDT) only (see Application.runReadAction())";
  @Internal
  public static final String MUST_NOT_EXECUTE_INSIDE_READ_ACTION =
    "Must not execute inside read action";
  private static final String MUST_EXECUTE_IN_WRITE_INTENT_READ_ACTION =
    "Access is allowed from write thread only";
  @Internal
  public static final String MUST_EXECUTE_INSIDE_WRITE_ACTION =
    "Write access is allowed inside write-action only (see Application.runWriteAction())";
  @Internal
  public static final String MUST_EXECUTE_UNDER_EDT =
    "Access is allowed from Event Dispatch Thread (EDT) only";
  @Internal
  public static final String MUST_NOT_EXECUTE_UNDER_EDT =
    "Access from Event Dispatch Thread (EDT) is not allowed";

  private static final String DOCUMENTATION_URL = "https://jb.gg/ij-platform-threading";

  /**
   * Asserts that the current thread is the event dispatch thread.
   */
  public static void assertEventDispatchThread() {
    if (!EDT.isCurrentThreadEdt()) {
      throwThreadAccessException(MUST_EXECUTE_UNDER_EDT);
    }
  }

  /**
   * Asserts that the current thread is <b>not</b> the event dispatch thread.
   */
  public static void assertBackgroundThread() {
    if (EDT.isCurrentThreadEdt()) {
      throwThreadAccessException(MUST_NOT_EXECUTE_UNDER_EDT);
    }
  }

  /**
   * Asserts that the current thread has read access.
   */
  public static void softAssertReadAccess() {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      getLogger().error(createThreadAccessException(MUST_EXECUTE_INSIDE_READ_ACTION));
    }
  }

  /**
   * Asserts that the current thread has <b>no</b> read access.
   */
  public static void assertNoReadAccess() {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      throwThreadAccessException(MUST_NOT_EXECUTE_INSIDE_READ_ACTION);
    }
  }

  /**
   * Asserts that the current thread has write-intent read access.
   */
  public static void assertWriteIntentReadAccess() {
    if (!ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
      throwThreadAccessException(MUST_EXECUTE_IN_WRITE_INTENT_READ_ACTION);
    }
  }

  /**
   * Asserts that the current thread has write access.
   */
  public static void assertWriteAccess() {
    if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throwThreadAccessException(MUST_EXECUTE_INSIDE_WRITE_ACTION);
    }
  }

  private static void throwThreadAccessException(@NotNull @NonNls String message) {
    throw createThreadAccessException(message);
  }

  private static @NotNull RuntimeExceptionWithAttachments createThreadAccessException(@NonNls @NotNull String message) {
    return new RuntimeExceptionWithAttachments(
      message + "; see " + DOCUMENTATION_URL + " for details" + "\n" + getThreadDetails(),
      new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString())
    );
  }

  private static @NotNull String getThreadDetails() {
    Thread current = Thread.currentThread();
    Thread edt = EDT.getEventDispatchThread();
    return "Current thread: " + describe(current) + " (EventQueue.isDispatchThread()=" + EventQueue.isDispatchThread() + ")\n" +
           "SystemEventQueueThread: " + (edt == current ? "(same)" : describe(edt));
  }

  private static @NotNull String describe(@Nullable Thread o) {
    return o == null ? "null" : o + " " + System.identityHashCode(o);
  }
}
