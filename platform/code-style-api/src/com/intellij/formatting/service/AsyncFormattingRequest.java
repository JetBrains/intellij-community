// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contains formatting data and methods handling formatting results.
 */
@ApiStatus.Experimental
public interface AsyncFormattingRequest {
  /**
   * @return The document text to be formatted.
   */
  @NotNull String getDocumentText();

  /**
   * @return A list of formatting ranges. It must be used by {@code asyncFormat()} implementation if {@code
   * AsyncDocumentFormattingService} supports range formatting: {@link FormattingService.Feature#FORMAT_FRAGMENTS} feature.
   */
  @NotNull List<TextRange> getFormattingRanges();

  /**
   * @return True if only whitespaces changes are allowed.
   */
  boolean canChangeWhitespaceOnly();

  /**
   * @return True if the service must provide a quick ad-hoc formatting rather than a long-lasting document processing.
   *
   * @see FormattingService.Feature#AD_HOC_FORMATTING
   */
  boolean isQuickFormat();

  /**
   * @return The current {@link FormattingContext}. Note: use {@link #getFormattingRanges()} instead of
   * {@link FormattingContext#getFormattingRange()} to get proper ranges which can be modified if formatting service supports range
   * formatting.
   */
  @NotNull FormattingContext getContext();

  /**
   * Call this method when resulting formatted text is available. If the original document has changed, the result will be merged with
   * an available {@link DocumentMerger} extension. If there are no suitable document merge extensions, the result will be ignored.
   * <p>
   * <b>Note:</b> {@code onTextReady()} may be called only once, subsequent calls will be ignored.
   * @param updatedText New document text.
   */
  void onTextReady(@NotNull String updatedText);

  /**
   * Show an error notification to an end user. The notification uses {@link AsyncDocumentFormattingService#getNotificationGroupId()}.
   * <p>
   * <b>Note:</b> {@code onError()} may be called only once, subsequent calls will be ignored.
   *
   * @param title The notification title.
   * @param message The notification message.
   */
  void onError(@NotNull @NlsContexts.NotificationTitle String title, @NotNull @NlsContexts.NotificationContent String message);

}
