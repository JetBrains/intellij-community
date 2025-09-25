// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.CodeStyleBundle;
import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.formatting.service.AsyncDocumentFormattingService.*;

@ApiStatus.Internal
public class AsyncDocumentFormattingSupportImpl implements AsyncDocumentFormattingSupport {
  private final AsyncDocumentFormattingService myService;
  private static final Logger LOG = Logger.getInstance(AsyncDocumentFormattingSupportImpl.class);
  private final List<AsyncFormattingRequest> myPendingRequests = Collections.synchronizedList(new ArrayList<>());
  private static final int RETRY_PERIOD = 1000; // milliseconds

  public AsyncDocumentFormattingSupportImpl(AsyncDocumentFormattingService service) { this.myService = service; }

  @Override
  public void formatDocument(@NotNull Document document,
                             @NotNull List<TextRange> formattingRanges,
                             @NotNull FormattingContext formattingContext,
                             boolean canChangeWhiteSpaceOnly,
                             boolean quickFormat) {
    AsyncFormattingRequest currRequest = findPendingRequest(document);
    boolean forceSync = Boolean.TRUE.equals(document.getUserData(FORMAT_DOCUMENT_SYNCHRONOUSLY));
    if (currRequest != null) {
      if (!((FormattingRequestImpl)currRequest).cancel()) {
        LOG.warn("Pending request can't be cancelled");
        return;
      }
    }
    prepareForFormatting(myService, document, formattingContext);
    FormattingRequestImpl formattingRequest = new FormattingRequestImpl(formattingContext, document, formattingRanges,
                                                                        canChangeWhiteSpaceOnly, quickFormat);
    FormattingTask formattingTask = createFormattingTask(myService, formattingRequest);
    if (formattingTask != null) {
      formattingRequest.setTask(formattingTask);
      myPendingRequests.add(formattingRequest);
      if (forceSync || ApplicationManager.getApplication().isHeadlessEnvironment()) {
        runAsyncFormat(formattingRequest, null);
      }
      else {
        if (formattingTask.isRunUnderProgress()) {
          new FormattingProgressTask(formattingRequest)
            .setCancelText(CodeStyleBundle.message("async.formatting.service.cancel", getName(myService)))
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

  private enum FormattingRequestState {
    NOT_STARTED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    EXPIRED
  }

  private class FormattingProgressTask extends Task.Backgroundable {
    private final FormattingRequestImpl myRequest;

    private FormattingProgressTask(@NotNull FormattingRequestImpl request) {
      super(request.getContext().getProject(), CodeStyleBundle.message("async.formatting.service.running", getName(myService)), true);
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

  private class FormattingRequestImpl implements AsyncFormattingRequest {
    private static final String TEMP_FILE_PREFIX = "ij-format-temp";

    private final Document          myDocument;
    private final List<TextRange>   myRanges;
    private final long              myInitialModificationStamp;
    private final FormattingContext myContext;
    private final boolean           myCanChangeWhitespaceOnly;
    private final boolean           myQuickFormat;
    private final Semaphore myTaskSemaphore = new Semaphore(1);

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
      myQuickFormat = quickFormat;
      myInitialModificationStamp = document.getModificationStamp();
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
          while (waitTime < getTimeout(myService).getSeconds() * 1000L) {
            if (myTaskSemaphore.tryAcquire(RETRY_PERIOD, TimeUnit.MILLISECONDS)) {
              myTaskSemaphore.release();
              break;
            }
            if (indicator != null) indicator.checkCanceled();
            waitTime += RETRY_PERIOD;
          }
          if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.EXPIRED)) {
            FormattingNotificationService.getInstance(myContext.getProject()).reportError(
              getNotificationGroupId(myService), getTimeoutNotificationDisplayId(myService), getName(myService),
              CodeStyleBundle.message("async.formatting.service.timeout", getName(myService),
                                      Long.toString(getTimeout(myService).getSeconds())),
              getTimeoutActions(myService, myContext));
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
      if (!needToUpdate(myService)) return;
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
    public void onTextReady(final @Nullable String updatedText) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myResult = updatedText;
        myTaskSemaphore.release();
      }
    }

    @Override
    public void onError(@NotNull @NlsContexts.NotificationTitle String title,
                        @NotNull @NlsContexts.NotificationContent String message,
                        @Nullable String displayId) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myTaskSemaphore.release();
        FormattingNotificationService.getInstance(myContext.getProject())
          .reportError(getNotificationGroupId(myService), displayId, title, message);
      }
    }

    @Override
    public void onError(@NotNull @NlsContexts.NotificationTitle String title,
                        @NotNull @NlsContexts.NotificationContent String message,
                        @Nullable String displayId,
                        int offset) {
      if (myStateRef.compareAndSet(FormattingRequestState.RUNNING, FormattingRequestState.COMPLETED)) {
        myTaskSemaphore.release();
        FormattingNotificationService.getInstance(myContext.getProject())
          .reportErrorAndNavigate(getNotificationGroupId(myService), displayId, title, message, myContext, offset);
      }
    }
  }
}
