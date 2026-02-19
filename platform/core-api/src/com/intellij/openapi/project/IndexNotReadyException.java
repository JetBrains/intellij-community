// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.util.Computable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown on accessing indices when they're not ready, in so-called dumb mode. Possible fixes:
 * <ul>
 * <li> If {@link com.intellij.openapi.actionSystem.AnAction#actionPerformed} is in stack trace,
 * consider making the action not implement {@link DumbAware}.
 *
 * <li> A {@link DumbAware} action, having got this exception, may just notify the user that the requested activity is not possible while
 * indexing is in progress. It can be done via a dialog (see {@link com.intellij.openapi.ui.Messages}) or a status bar balloon
 * (see {@link DumbService#showDumbModeNotificationForAction(String, String)},
 * {@link DumbService#showDumbModeNotificationForFunctionality(String, DumbModeBlockedFunctionality)},
 * {@link com.intellij.openapi.actionSystem.ex.ActionUtil#showDumbModeWarning(Project, com.intellij.openapi.actionSystem.AnAction, com.intellij.openapi.actionSystem.AnActionEvent...)}).
 * In case of using custom UI please report this event with {@link DumbModeBlockedFunctionalityCollector}
 *
 * <li> If index access is performed from some non-urgent invokeLater activity, consider replacing it with
 * {@link DumbService#smartInvokeLater(Runnable)}. Note that this 'later' can be very late, several minutes may pass. So if that code
 * involves user interaction, {@link DumbService#smartInvokeLater(Runnable)} should probably not be used to avoid dialogs popping out of the blue.
 *
 * <li> If it's a non-urgent background process (e.g. compilation, usage search), consider replacing topmost read-action with
 * {@link DumbService#runReadActionInSmartMode(Computable)}.
 *
 * <li> If the exception comes from within Java's findClass call, and the IDE is currently performing a user-initiated action or a
 * task when skipping findClass would lead to very negative consequences (e.g. not stopping at a breakpoint), then it might be possible
 * to avoid index query by using alternative resolve (and findClass) strategy, which is significantly slower and might return null. To do this,
 * use {@link DumbService#runWithAlternativeResolveEnabled(ThrowableRunnable)} or similar.
 *
 * <li> If you're performing a long modal operation which leads to a root change in the middle (or otherwise causes indexing),
 * but you need indices after that, you can call {@link DumbService#completeJustSubmittedTasks()} before performing
 * those index queries.
 *
 * <li> It's preferable to avoid the exception entirely by adding {@link DumbService#isDumb()} checks where necessary.
 * </ul>
 *
 * @see DumbService
 * @see DumbAware
 */
public final class IndexNotReadyException extends RuntimeException implements ExceptionWithAttachments {
  private final @Nullable Throwable myStartTrace;

  // constructor is private to not let ForkJoinTask.getThrowableException() clone this by reflection causing invalid nesting etc
  private IndexNotReadyException(@Nullable Throwable startTrace, @Nullable Throwable cause) {
    super("Please change caller according to " + IndexNotReadyException.class.getName() + " documentation. " +
          "Dumb mode start trace is in attachment.", cause);
    myStartTrace = startTrace;
  }

  @Override
  public Attachment @NotNull [] getAttachments() {
    return myStartTrace == null
           ? Attachment.EMPTY_ARRAY
           : new Attachment[]{new Attachment("indexingStart", myStartTrace)};
  }

  public static @NotNull IndexNotReadyException create() {
    return create(null);
  }

  public static @NotNull IndexNotReadyException create(@Nullable Throwable startTrace) {
    return new IndexNotReadyException(startTrace, convertStartTraceToCauseInUnitTests(startTrace));
  }

  private static @Nullable Throwable convertStartTraceToCauseInUnitTests(@Nullable Throwable startTrace) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return startTrace;
    }
    else {
      return null;
    }
  }
}
