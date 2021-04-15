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
 * in {@link #asyncFormat(AsyncFormattingRequest)} method which may be slow. A cancellable operation in {@code asyncFormat} method must be
 * placed into {@link #runCancellable(AsyncFormattingRequest, AsyncFormattingRequest.CancellableRunnable)} method, for example:
 * <pre><code>
 *   void asyncFormat(AsyncFormattingRequest request) {
 *     ...
 *     runCancellable(request, new CancellableRunnable() {
 *       ...
 *       boolean cancel() {
 *         // Cancellation code
 *       }
 *     }
 *   }
 * </code></pre>
 * If another {@code formatDocument()} call is made for the same document, the previous request is cancelled. On success, if
 * {@code cancel()} returns {@code true}, another request replaces the previous one. Otherwise the newer request is rejected.
 * <p>
 * Before the actual formatting starts, {@link #prepare(FormattingContext)} method is called. It should be fast enough not to block EDT.
 * If it succeeds (returns {@code true}), {@link #asyncFormat(AsyncFormattingRequest)} method is invoked.
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
    if (prepare(formattingContext)) {
      AsyncFormattingRequest formattingRequest = new FormattingRequestImpl(formattingContext, document, canChangeWhiteSpaceOnly);
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

  private void runAsyncFormat(@NotNull AsyncFormattingRequest formattingRequest) {
    myPendingRequests.add(formattingRequest);
    try {
      asyncFormat(formattingRequest);
    }
    finally {
      myPendingRequests.remove(formattingRequest);
    }
  }

  /**
   * Called before the actual formatting starts. Any specific data can be put to {@link FormattingContext}'s user data. The same
   * {@code FormattingContext} is passed then to {@link #asyncFormat(AsyncFormattingRequest)}.
   *
   * @param formattingContext The formatting context to use and store specific data.
   * @return {@code true} if successful and formatting can proceed, {@code false} otherwise. The latter may be a result, for example,
   * of misconfiguration.
   */
  protected abstract boolean prepare(@NotNull FormattingContext formattingContext);

  /**
   * Format a document using the {@link AsyncFormattingRequest}.
   * <p>
   * {@code asyncFormat()} method is run asynchronously on a pooled thread. The method may be slow and perform I/O operations and/or
   * run system processes. It is a responsibility of an implementor to handle timeouts if necessary.
   * <p>
   * Call {@link AsyncFormattingRequest#getDocumentText()} to get a document text. Use {@link AsyncFormattingRequest#getContext()} for
   * more context information. Return result via {@link AsyncFormattingRequest#onTextReady(String)} or call
   * {@link AsyncFormattingRequest#onError(String, String)}. In case of an error, a notification will be shown to an end user.
   * <p>
   * For example:
   * <pre><code>
   *   void asyncFormat(AsyncFormattingRequest request) {
   *     ...
   *     if (error) {
   *       request.onError(title, message);
   *       return;
   *     }
   *     request.onTextReady(formattedText);
   *   }
   * </code></pre>
   *
   * @param request The formatting request to process.
   */
  protected abstract void asyncFormat(@NotNull AsyncFormattingRequest request);

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
   * Run a long-lasting cancellable runnable.
   *
   * @param currentRequest The current request passed to {@link #asyncFormat(AsyncFormattingRequest)} as a parameter.
   * @param runnable The cancellable runnable.
   */
  protected final void runCancellable(@NotNull AsyncFormattingRequest currentRequest,
                                      @NotNull AsyncFormattingRequest.CancellableRunnable runnable) {
    FormattingRequestImpl request = (FormattingRequestImpl)currentRequest;
    if (request.myCancellableRunnable != null) {
      LOG.error("Another CancellableRunnable is currently active, can't run two of them simultaneously.");
    }
    try {
      request.setCancellableRunnable(runnable);
      runnable.run();
    }
    finally {
      request.setCancellableRunnable(null);
    }
  }


  /**
   * @return A notification group ID to use when error messages are shown to an end user.
   */
  protected abstract @NotNull String getNotificationGroupId();

  private class FormattingRequestImpl implements AsyncFormattingRequest {
    private final Document          myDocument;
    private final long              myInitialModificationStamp;
    private final FormattingContext myContext;
    private final boolean           myCanChangeWhitespaceOnly;

    private volatile @Nullable CancellableRunnable myCancellableRunnable;

    private FormattingRequestImpl(@NotNull FormattingContext formattingContext,
                                  @NotNull Document document,
                                  boolean canChangeWhitespaceOnly) {
      myContext = formattingContext;
      myDocument = document;
      myCanChangeWhitespaceOnly = canChangeWhitespaceOnly;
      myInitialModificationStamp = document.getModificationStamp();
    }

    @Override
    public @NotNull String getDocumentText() {
      return myDocument.getText();
    }

    private boolean cancel() {
      CancellableRunnable runnable = myCancellableRunnable;
      if (runnable != null) {
        return runnable.cancel();
      }
      return false;
    }

    private Document getDocument() {
      return myDocument;
    }

    @Override
    public boolean canChangeWhitespaceOnly() {
      return myCanChangeWhitespaceOnly;
    }

    @Override
    public @NotNull FormattingContext getContext() {
      return myContext;
    }

    private void setCancellableRunnable(@Nullable CancellableRunnable cancellableRunnable) {
      myCancellableRunnable = cancellableRunnable;
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
}
