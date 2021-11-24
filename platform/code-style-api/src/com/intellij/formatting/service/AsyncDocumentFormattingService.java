// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.CodeStyleBundle;
import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
  private final static Logger LOG = Logger.getInstance(AsyncDocumentFormattingService.class);

  private final List<AsyncFormattingRequest> myPendingRequests = Collections.synchronizedList(new ArrayList<>());

  protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
  private static final int RETRY_PERIOD = 1000; // milliseconds

  @Override
  public final synchronized void formatDocument(@NotNull Document document,
                                                @NotNull List<TextRange> formattingRanges,
                                                @NotNull FormattingContext formattingContext,
                                                boolean canChangeWhiteSpaceOnly,
                                                boolean quickFormat) {
    AsyncFormattingRequest currRequest = findPendingRequest(document);
    if (currRequest != null) {
      if (!((FormattingRequestImpl)currRequest).cancel()) {
        LOG.warn("Pending request can't be cancelled");
        return;
      }
    }
    FormattingRequestImpl formattingRequest = new FormattingRequestImpl(formattingContext, document, formattingRanges,
                                                                         canChangeWhiteSpaceOnly, quickFormat);
    FormattingTask formattingTask = createFormattingTask(formattingRequest);
    if (formattingTask != null) {
      formattingRequest.setTask(formattingTask);
      myPendingRequests.add(formattingRequest);
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        runAsyncFormat(formattingRequest, null);
      }
      else {
        if (formattingTask.isRunUnderProgress()) {
          new FormattingProgressTask(formattingRequest)
            .setCancelText(CodeStyleBundle.message("async.formatting.service.cancel", getName()))
            .queue();
        }
        else {
          ApplicationManager.getApplication().executeOnPooledThread(() -> runAsyncFormat(formattingRequest, null));
        }
      }
    }
  }

  private @Nullable AsyncFormattingRequest findPendingRequest(@NotNull Document document) {
    synchronized (myPendingRequests) {
      return ContainerUtil
        .find(myPendingRequests, request -> ((FormattingRequestImpl)request).getDocument() == document);
    }
  }

  private void runAsyncFormat(@NotNull FormattingRequestImpl formattingRequest, @Nullable ProgressIndicator indicator) {
    try {
      formattingRequest.runTask(indicator);
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
  protected abstract @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest);

  /**
   * @return A notification group ID to use when error messages are shown to an end user.
   */
  protected abstract @NotNull String getNotificationGroupId();

  /**
   * @return A name which can be used in UI, for example, in notification messages.
   */
  protected abstract @NotNull @NlsSafe String getName();

  /**
   * @return A duration to wait for the service to respond (call either {@code onTextReady()} or {@code onError()}).
   */
  protected Duration getTimeout() {
    return DEFAULT_TIMEOUT;
  }

  private enum FormattingRequestState {
    NOT_STARTED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    EXPIRED
  }

  private class FormattingRequestImpl implements AsyncFormattingRequest {
    private final static String TEMP_FILE_PREFIX = "ij-format-temp";

    private final Document          myDocument;
    private final List<TextRange>   myRanges;
    private final long              myInitialModificationStamp;
    private final FormattingContext myContext;
    private final boolean           myCanChangeWhitespaceOnly;
    private final boolean           myQuickFormat;
    private final Semaphore         myTaskSemaphore = new Semaphore(1);

    private volatile @Nullable FormattingTask myTask;

    private @Nullable String myResult;

    private final AtomicReference<FormattingRequestState> myStateRef = new AtomicReference<>(FormattingRequestState.NOT_STARTED);

    private FormattingRequestImpl(@NotNull FormattingContext formattingContext,
                                  @NotNull Document document,
                                  @NotNull List<TextRange> ranges,
                                  boolean canChangeWhitespaceOnly,
                                  boolean quickFormat) {
      myContext = formattingContext;
      myDocument = document;
      myRanges = ranges;
      myCanChangeWhitespaceOnly = canChangeWhitespaceOnly;
      myInitialModificationStamp = document.getModificationStamp();
      myQuickFormat = quickFormat;
      FileDocumentManager.getInstance().saveDocument(myDocument);
    }

    @Override
    public @Nullable File getIOFile() {
      VirtualFile originalFile = myContext.getVirtualFile();
      String ext;
      Charset charset;
      if (originalFile != null) {
        if (originalFile.isInLocalFileSystem()) {
          Path localPath = originalFile.getFileSystem().getNioPath(originalFile);
          if (localPath != null) {
            return localPath.toFile();
          }
        }
        ext = originalFile.getExtension();
        charset = originalFile.getCharset();
      }
      else {
        ext = myContext.getContainingFile().getFileType().getDefaultExtension();
        charset = EncodingManager.getInstance().getDefaultCharset();
      }
      try {
        File tempFile = FileUtilRt.createTempFile(TEMP_FILE_PREFIX, "." + ext, true);
        try (FileWriter writer = new FileWriter(tempFile, charset)) {
          writer.write(getDocumentText());
        }
        return tempFile;
      }
      catch (IOException e) {
        LOG.warn(e);
        return null;
      }
    }

    @Override
    public @NotNull String getDocumentText() {
      return myDocument.getText();
    }

    private boolean cancel() {
      FormattingTask formattingTask = myTask;
      if (formattingTask != null && myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.CANCELLING)) {
        if (formattingTask.cancel()) {
          myStateRef.set(FormattingRequestState.CANCELLED);
          myTaskSemaphore.release();
          return true;
        }
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

    private void runTask(@Nullable ProgressIndicator indicator) {
      FormattingTask task = myTask;
      if (task != null && myStateRef.compareAndSet(FormattingRequestState.NOT_STARTED, FormattingRequestState.RUNNING)) {
        try {
          myTaskSemaphore.acquire();
          task.run();
          long waitTime = 0;
          while (waitTime < getTimeout().getSeconds() * 1000L) {
            if (myTaskSemaphore.tryAcquire(RETRY_PERIOD, TimeUnit.MILLISECONDS)) {
              myTaskSemaphore.release();
              break;
            }
            if (indicator != null) indicator.checkCanceled();
            waitTime += RETRY_PERIOD;
          }
          if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.EXPIRED)) {
            FormattingNotificationService.getInstance(myContext.getProject()).reportError(
              getNotificationGroupId(), getName(),
              CodeStyleBundle.message("async.formatting.service.timeout", getName(), Long.toString(getTimeout().getSeconds())));
          }
          else if (myResult != null) {
            if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
              updateDocument(myResult);
            }
            else {
              ApplicationManager.getApplication().invokeLater(() -> {
                CommandProcessor.getInstance().runUndoTransparentAction(() -> {
                  try {
                    WriteAction.run((ThrowableRunnable<Throwable>)() -> {
                      updateDocument(myResult);
                    });
                  }
                  catch (Throwable throwable) {
                    LOG.error(throwable);
                  }
                });
              });
            }
          }
        }
        catch (InterruptedException ie) {
          LOG.warn("Interrupted formatting thread.");
        }
      }
    }

    private void updateDocument(@NotNull String newText) {
      if (myDocument.getModificationStamp() > myInitialModificationStamp) {
        for (DocumentMerger merger : DocumentMerger.EP_NAME.getExtensionList()) {
          if (merger.updateDocument(myDocument, newText)) break;
        }
      }
      else {
        myDocument.setText(newText);
      }
    }

    @Override
    public boolean isQuickFormat() {
      return myQuickFormat;
    }

    @Override
    public void onTextReady(@NotNull final String updatedText) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myResult = updatedText;
        myTaskSemaphore.release();
      }
    }

    @Override
    public void onError(@NotNull @NlsContexts.NotificationTitle String title, @NotNull @NlsContexts.NotificationContent String message) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myTaskSemaphore.release();
        FormattingNotificationService.getInstance(myContext.getProject()).reportError(getNotificationGroupId(), title, message);
      }
    }

    @Override
    public void onError(@NotNull @NlsContexts.NotificationTitle String title,
                        @NotNull @NlsContexts.NotificationContent String message,
                        int offset) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myTaskSemaphore.release();
        FormattingNotificationService.getInstance(myContext.getProject())
                                     .reportErrorAndNavigate(getNotificationGroupId(), title, message, myContext, offset);
      }
    }
  }


  protected interface FormattingTask extends Runnable {
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

  private class FormattingProgressTask extends Task.Backgroundable {
    private final FormattingRequestImpl myRequest;

    private FormattingProgressTask(@NotNull FormattingRequestImpl request) {
      super(request.getContext().getProject(), CodeStyleBundle.message("async.formatting.service.running", getName()), true);
      myRequest = request;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      indicator.setFraction(0.0);
      runAsyncFormat(myRequest, indicator);
      indicator.setFraction(1.0);
    }

    @Override
    public void onCancel() {
      myRequest.cancel();
    }
  }
}
