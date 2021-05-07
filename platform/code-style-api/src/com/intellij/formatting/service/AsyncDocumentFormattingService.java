// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extend this class if there is a long lasting formatting operation which may block EDT. The actual formatting code is placed then
 * in {@link FormattingTask#run()} method which may be slow.
 * <p>
 * If another {@code formatDocument()} call is made for the same document, the previous request is cancelled. On success, if
 * {@code cancel()} returns {@code true}, another request replaces the previous one. Otherwise the newer request is rejected.
 * <p>
 * Before the actual formatting starts, {@link #createFormattingTask(AsyncFormattingRequest)} method is called. It should be fast enough not to
 * block EDT. If it succeeds (doesn't return null), further formatting is started using the created runnable on a separate thread.
 */
@ApiStatus.Experimental
public abstract class AsyncDocumentFormattingService extends AbstractDocumentFormattingService {
  private final static Logger LOG = Logger.getInstance(AsyncDocumentFormattingService.class);

  private final List<AsyncFormattingRequest> myPendingRequests = Collections.synchronizedList(new ArrayList<>());

  @Override
  public final synchronized void formatDocument(@NotNull Document document,
                                                @NotNull List<TextRange> formattingRanges,
                                                @NotNull FormattingContext formattingContext,
                                                boolean canChangeWhiteSpaceOnly) {
    AsyncFormattingRequest currRequest = findPendingRequest(document);
    if (currRequest != null) {
      if (!((FormattingRequestImpl)currRequest).cancel()) {
        LOG.warn("Pending request can't be cancelled");
        return;
      }
    }
    FormattingRequestImpl formattingRequest = new FormattingRequestImpl(formattingContext, document, formattingRanges,
                                                                         canChangeWhiteSpaceOnly);
    FormattingTask formattingTask = createFormattingTask(formattingRequest);
    if (formattingTask != null) {
      formattingRequest.setTask(formattingTask);
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        runAsyncFormat(formattingRequest);
      }
      else {
        ApplicationManager.getApplication().executeOnPooledThread(() -> runAsyncFormat(formattingRequest));
      }
    }
  }

  private @Nullable AsyncFormattingRequest findPendingRequest(@NotNull Document document) {
    synchronized (myPendingRequests) {
      return ContainerUtil
        .find(myPendingRequests, request -> ((FormattingRequestImpl)request).getDocument() == document);
    }
  }

  private void runAsyncFormat(@NotNull FormattingRequestImpl formattingRequest) {
    myPendingRequests.add(formattingRequest);
    try {
      formattingRequest.runTask();
    }
    finally {
      myPendingRequests.remove(formattingRequest);
    }
  }

  /**
   * Called before the actual formatting starts.
   *
   * @param formattingRequest The formatting request to create the formatting task for.
   * @return {@link FormattingTask} if successful and formatting can proceed, {@code null} otherwise. The latter may be a result, for
   * example, of misconfiguration.
   */
  protected abstract FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest);

  /**
   * Merge changes if the document has changed since {@code asyncFormat()} was called. The default implementation does nothing and
   * rejects the updated text.
   *
   * @param document The current document.
   * @param updatedText The updated text to merge into the documents.
   */
  @SuppressWarnings("unused")
  protected void mergeChanges(@NotNull Document document, @NotNull String updatedText) {}

  /**
   * @return A notification group ID to use when error messages are shown to an end user.
   */
  protected abstract @NotNull String getNotificationGroupId();

  private class FormattingRequestImpl implements AsyncFormattingRequest {
    private final Document          myDocument;
    private final List<TextRange>   myRanges;
    private final long              myInitialModificationStamp;
    private final FormattingContext myContext;
    private final boolean           myCanChangeWhitespaceOnly;

    private volatile @Nullable FormattingTask myTask;

    private FormattingRequestImpl(@NotNull FormattingContext formattingContext,
                                  @NotNull Document document,
                                  @NotNull List<TextRange> ranges,
                                  boolean canChangeWhitespaceOnly) {
      myContext = formattingContext;
      myDocument = document;
      myRanges = ranges;
      myCanChangeWhitespaceOnly = canChangeWhitespaceOnly;
      myInitialModificationStamp = document.getModificationStamp();
    }

    @Override
    public @NotNull String getDocumentText() {
      return myDocument.getText();
    }

    private boolean cancel() {
      FormattingTask runnable = myTask;
      if (runnable != null) {
        return runnable.cancel();
      }
      return false;
    }

    private Document getDocument() {
      return myDocument;
    }

    @Override
    public @NotNull List<TextRange> getFormattingRanges() {
      return myRanges;
    }

    @Override
    public boolean canChangeWhitespaceOnly() {
      return myCanChangeWhitespaceOnly;
    }

    @Override
    public @NotNull FormattingContext getContext() {
      return myContext;
    }

    private void setTask(@Nullable FormattingTask formattingTask) {
      myTask = formattingTask;
    }

    private void runTask() {
      ObjectUtils.consumeIfNotNull(myTask, Runnable::run);
    }

    @Override
    public void onTextReady(@NotNull final String updatedText) {
      ApplicationManager.getApplication().invokeLater(() ->{
        CommandProcessor.getInstance().runUndoTransparentAction(() -> {
          try {
            WriteAction.run((ThrowableRunnable<Throwable>)() -> {
              if (myDocument.getModificationStamp() > myInitialModificationStamp) {
                mergeChanges(myDocument, updatedText);
              }
              else {
                myDocument.setText(updatedText);
              }
            });
          }
          catch (Throwable throwable) {
            LOG.error(throwable);
          }
        });
      });
    }

    @Override
    public void onError(@NotNull @NlsContexts.NotificationTitle String title, @NotNull @NlsContexts.NotificationContent String message) {
      FormattingNotificationService.getInstance(myContext.getProject()).reportError(getNotificationGroupId(), title, message);
    }
  }


  protected interface FormattingTask extends Runnable {
    /**
     * Cancel the current runnable.
     * @return {@code true} if the runnable has been successfully cancelled, {@code false} otherwise.
     */
    boolean cancel();
  }
}
