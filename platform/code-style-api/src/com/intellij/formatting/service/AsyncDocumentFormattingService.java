// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * Extend this class if there is a long-lasting formatting operation which may block EDT. The actual formatting code is placed then
 * in {@link FormattingTask#run()} method which may be slow.
 * <p>
 * If another {@code formatDocument()} call is made for the same document, the previous request is cancelled. On success, if
 * {@code cancel()} returns {@code true}, another request replaces the previous one. Otherwise, the newer request is rejected.
 * <p>
 * Before the actual formatting starts, {@link #createFormattingTask(AsyncFormattingRequest)} method is called. It should be fast enough not to
 * block EDT. If it succeeds (doesn't return null), further formatting is started using the created runnable on a separate thread.
 */
public abstract class AsyncDocumentFormattingService extends AbstractDocumentFormattingService {
  public static final Key<Boolean> FORMAT_DOCUMENT_SYNCHRONOUSLY =
    Key.create("com.intellij.formatting.service.AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY");

  protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private final AsyncDocumentFormattingSupport mySupport = ApplicationManager.getApplication()
    .getService(AsyncDocumentFormattingSupportFactory.class).create(this);

  @Override
  public final void formatDocument(@NotNull Document document,
                                   @NotNull List<TextRange> formattingRanges,
                                   @NotNull FormattingContext formattingContext,
                                   boolean canChangeWhiteSpaceOnly,
                                   boolean quickFormat) {
    mySupport.formatDocument(document, formattingRanges, formattingContext, canChangeWhiteSpaceOnly, quickFormat);
  }

  /**
   * Called before the actual formatting starts.
   *
   * @param formattingRequest The formatting request to create the formatting task for.
   * @return {@link FormattingTask} if successful and formatting can proceed, {@code null} otherwise. The latter may be a result, for
   * example, of misconfiguration.
   */
  protected abstract @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest);

  /**
   * @return A notification group ID to use when error messages are shown to an end user.
   */
  protected abstract @NotNull String getNotificationGroupId();

  /**
   * @return A notification display ID to use when timeout error messages are shown to an end user.
   */
  protected @Nullable String getTimeoutNotificationDisplayId() {
    return null;
  }

  protected boolean needToUpdate() {
    return true;
  }

  /**
   * @return A name which can be used in UI, for example, in notification messages.
   */
  protected abstract @NotNull @NlsSafe String getName();

  /**
   * Hook called before creating a formatting request. Default implementation saves the document
   * to ensure external formatters working with IO files see the latest content.
   */
  protected void prepareForFormatting(@NotNull Document document, @NotNull FormattingContext formattingContext) {
    FileDocumentManager.getInstance().saveDocument(document);
  }

  /**
   * @return A duration to wait for the service to respond (call either {@code onTextReady()} or {@code onError()}).
   */
  protected Duration getTimeout() {
    return DEFAULT_TIMEOUT;
  }

  protected AnAction[] getTimeoutActions(@NotNull FormattingContext context) {
    return AnAction.EMPTY_ARRAY;
  }

  @ApiStatus.OverrideOnly
  public interface FormattingTask extends Runnable {
    /**
     * Cancel the current runnable.
     * @return {@code true} if the runnable has been successfully cancelled, {@code false} otherwise.
     */
    boolean cancel();

    /**
     * @return True if the task must be run under progress (a progress indicator is created automatically). Otherwise, the task is
     * responsible for visualizing the progress by itself, it is just started on a background thread.
     */
    default boolean isRunUnderProgress() {
      return false;
    }
  }

  // region Internal Accessors
  @ApiStatus.Internal
  public static FormattingTask createFormattingTask(@NotNull AsyncDocumentFormattingService service,
                                                    @NotNull AsyncFormattingRequest request) {
    return service.createFormattingTask(request);
  }

  @ApiStatus.Internal
  public static @NotNull String getNotificationGroupId(@NotNull AsyncDocumentFormattingService service) {
    return service.getNotificationGroupId();
  }

  @ApiStatus.Internal
  public static @Nullable String getTimeoutNotificationDisplayId(@NotNull AsyncDocumentFormattingService service) {
    return service.getTimeoutNotificationDisplayId();
  }

  @ApiStatus.Internal
  public static boolean needToUpdate(@NotNull AsyncDocumentFormattingService service) {
    return service.needToUpdate();
  }

  @ApiStatus.Internal
  public static @NotNull @NlsSafe String getName(@NotNull AsyncDocumentFormattingService service) {
    return service.getName();
  }

  @ApiStatus.Internal
  public static void prepareForFormatting(@NotNull AsyncDocumentFormattingService service,
                                          @NotNull Document document,
                                          @NotNull FormattingContext formattingContext) {
    service.prepareForFormatting(document, formattingContext);
  }

  @ApiStatus.Internal
  public static Duration getTimeout(@NotNull AsyncDocumentFormattingService service) {
    return service.getTimeout();
  }

  @ApiStatus.Internal
  public static AnAction[] getTimeoutActions(@NotNull AsyncDocumentFormattingService service,
                                             @NotNull FormattingContext context) {
    return service.getTimeoutActions(context);
  }
  // endregion
}
