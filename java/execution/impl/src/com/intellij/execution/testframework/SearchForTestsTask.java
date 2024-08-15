// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class SearchForTestsTask extends Task.Backgroundable {
  public static final Key<Boolean> CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY = Key.create("SearchForTestsTask.connect.in.unit.test.mode");

  private static final Logger LOG = Logger.getInstance(SearchForTestsTask.class);
  protected Socket mySocket;
  private final ServerSocket myServerSocket;
  private ProgressIndicator myProcessIndicator;
  private boolean myAllowIndexInDumbMode;
  @NotNull private Runnable myIncompleteIndexUsageCallback = EmptyRunnable.getInstance();

  public SearchForTestsTask(@Nullable final Project project,
                            final ServerSocket socket) {
    super(project, ExecutionBundle.message("searching.test.progress.title"), true);
    myServerSocket = socket;
  }


  protected abstract void search() throws ExecutionException;
  protected abstract void onFound() throws ExecutionException;

  public void ensureFinished() {
    if (myProcessIndicator != null && !myProcessIndicator.isCanceled()) {
      finish();
    }
  }

  public void startSearch() {
    if (ApplicationManager.getApplication().isUnitTestMode() && !TestModeFlags.is(CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY)) {
      try {
        search();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      try {
        onFound();
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    }
    else {
      myProcessIndicator = new BackgroundableProcessIndicator(this);
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(this, myProcessIndicator);
    }
  }

  public void attachTaskToProcess(final OSProcessHandler handler) {
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull final ProcessEvent event) {
        handler.removeProcessListener(this);
        ensureFinished();
      }

      @Override
      public void startNotified(@NotNull final ProcessEvent event) {
        startSearch();
      }
    });
  }

  protected boolean requiresSmartMode() {
    return true;
  }
  
  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      LOG.info("Waiting for connection on port " + myServerSocket.getLocalPort());
      mySocket = myServerSocket.accept();
      final ExecutionException[] ex = new ExecutionException[1];
      NonBlockingReadAction<Void> readAction = ReadAction.nonBlocking(() -> performWithIncompleteIndex(this::search, ex));
      if (requiresSmartMode() && !myAllowIndexInDumbMode) {
        readAction = readAction.inSmartMode(myProject);
      }
      readAction.executeSynchronously();
      if (ex[0] != null) {
        logCantRunException(ex[0]);
      }

      ExecutionException[] onFoundEx = new ExecutionException[1];
      Runnable runnable = () -> performWithIncompleteIndex(this::onFound, onFoundEx);
      if (requiresSmartMode() && !myAllowIndexInDumbMode) {
        DumbService.getInstance(getProject()).runReadActionInSmartMode(runnable);
      }
      else {
        ReadAction.run(runnable::run);
      }
      if (onFoundEx[0] != null) {
        throw onFoundEx[0];
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  protected void logCantRunException(ExecutionException e) throws ExecutionException {
    throw e;
  }

  @Override
  public void onCancel() {
    finish();
  }

  @Override
  public void onSuccess() {
    Runnable runnable = this::finish;
    if (requiresSmartMode() && !myAllowIndexInDumbMode) {
      DumbService.getInstance(getProject()).runWhenSmart(runnable);
    }
    else {
      runnable.run();
    }
  }

  private void performWithIncompleteIndex(ThrowableRunnable<ExecutionException> action, ExecutionException[] ex) {
    try {
      if (myAllowIndexInDumbMode && DumbService.isDumb(myProject)) {
        myIncompleteIndexUsageCallback.run();
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
          action.run();
          return null;
        });
      } else {
        action.run();
      }
    }
    catch (ExecutionException e) {
      ex[0] = e;
    }
  }

  public void finish() {
    DataOutputStream os = null;
    try {
      if (mySocket == null || mySocket.isClosed()) return;
      os = new DataOutputStream(mySocket.getOutputStream());
      os.writeBoolean(true);
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    finally {
      try {
        if (os != null) os.close();
      }
      catch (Throwable e) {
        LOG.info(e);
      }

      try {
        if (!myServerSocket.isClosed()) {
          myServerSocket.close();
        }
      }
      catch (Throwable e) {
        LOG.info(e);
      }
    }
  }

  public void setIncompleteIndexUsageCallback(@NotNull Runnable incompleteIndexUsageCallback) {
    myIncompleteIndexUsageCallback = incompleteIndexUsageCallback;
  }

  @ApiStatus.Internal
  public void arrangeForIndexAccess() {
    if (!requiresSmartMode() || !DumbService.isDumb(myProject)) return;

    AtomicBoolean canProceedWithTests = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      JLabel component = new JLabel(new HtmlBuilder()
                                      .appendRaw(ExecutionBundle.message("tests.wait.or.use.partial.index"))
                                      .wrapWithHtmlBody()
                                      .toString());

      DialogWrapper dialog = new DialogWrapper(myProject) {
        {
          setTitle(IndexingBundle.message("progress.indexing.updating"));
          setOKButtonText(ExecutionBundle.message("test.button.run.with.partial.index"));
          init();
          LaterInvocator.markTransparent(ModalityState.stateForComponent(component));
        }

        @Override
        protected JComponent createCenterPanel() {
          return component;
        }
      };

      myProject.getMessageBus().connect(dialog.getDisposable()).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void exitDumbMode() {
          canProceedWithTests.set(true);
          Window window = ComponentUtil.getWindow(component);
          if (window != null) {
            window.setVisible(false);
          }
        }
      });
      if (dialog.showAndGet()) {
        myAllowIndexInDumbMode = true;
        canProceedWithTests.set(true);
      }
    });

    if (canProceedWithTests.get()) {
      return;
    }
    throw new ProcessCanceledException();
  }
}
