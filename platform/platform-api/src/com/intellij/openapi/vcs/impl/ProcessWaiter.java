package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.process.InterruptibleProcess;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class ProcessWaiter<T extends CancellableRunnable> {
  protected T myInStreamListener;
  protected T myErrStreamListener;

  protected abstract T createStreamListener(final InputStream stream);
  protected boolean tryReadStreams(final int rc) {
    return true;
  }

  public int execute(final InterruptibleProcess worker, final long timeout) throws IOException, ExecutionException, TimeoutException, InterruptedException {
    myErrStreamListener = createStreamListener(worker.getErrorStream());
    myInStreamListener = createStreamListener(worker.getInputStream());

    final Application app = ApplicationManager.getApplication();
    Future<?> errorStreamReadingFuture = null;
    Future<?> outputStreamReadingFuture = null;

    final int rc;
    try {
      errorStreamReadingFuture = app.executeOnPooledThread(myErrStreamListener);
      outputStreamReadingFuture = app.executeOnPooledThread(myInStreamListener);
      rc = worker.execute();
      if (tryReadStreams(rc)) {
        errorStreamReadingFuture.get(timeout, TimeUnit.MILLISECONDS);
        outputStreamReadingFuture.get(timeout, TimeUnit.MILLISECONDS);
      }
    } finally {
      cancelListeners();
      if (errorStreamReadingFuture != null) {
        errorStreamReadingFuture.cancel(true);
      }
      if (outputStreamReadingFuture != null) {
        outputStreamReadingFuture.cancel(true);
      }
    }

    return rc;
  }

  public void cancelListeners() {
    myErrStreamListener.cancel();
    myInStreamListener.cancel();
  }

  public T getInStreamListener() {
    return myInStreamListener;
  }

  public T getErrStreamListener() {
    return myErrStreamListener;
  }
}
