// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.TraceableDisposable;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicInteger;

public class DaemonProgressIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
  private static final Logger LOG = Logger.getInstance(DaemonProgressIndicator.class);
  static final String CANCEL_WAS_CALLED_REASON = "cancel() was called";
  private static final AtomicInteger debug = new AtomicInteger(); // if >0 then it's in the debug mode
  private final TraceableDisposable myTraceableDisposable = new TraceableDisposable(debug.get()>0);
  private volatile Throwable myCancellationCause;
  private volatile Span mySpan;
  private final IJTracer myTraceManager = TelemetryManager.Companion.getInstance().getTracer(new Scope("daemon", null));

  @Override
  public final void stop() {
    if(mySpan != null) {
      mySpan.end();
    }
    if (tryStop()) {
      onStop();
    }
  }

  // return true if stopped successfully
  private boolean tryStop() {
    boolean wasRunning;
    synchronized (getLock()) {
      wasRunning = isRunning();
      if (wasRunning) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("stop(" + this + ")");
        }
        super.stop();
      }
      tryCancel(null); // do not call onCancelled in stop()
    }
    return wasRunning;
  }

  // must be called under getLock()
  private boolean tryCancel(@Nullable Throwable cause) {
    if (!isCanceled()) {
      // save before cancel to avoid data race with "checkCanceled(); catch(PCE) { check saved Exception }" elsewhere
      if (cause == null) {
        myTraceableDisposable.kill("Daemon Progress Canceled");
      }
      else {
        myTraceableDisposable.killExceptionally(cause);
      }
      myCancellationCause = cause;

      super.cancel();
      return true;
    }
    return false;
  }

  protected void onCancelled(@NotNull String reason) { }

  protected void onStop() { }

  @Override
  public final void cancel() {
    Throwable cause = LOG.isDebugEnabled() ? new Throwable(CANCEL_WAS_CALLED_REASON) : null;
    doCancel(cause, CANCEL_WAS_CALLED_REASON);
  }

  public final void cancel(@NotNull String reason) {
    doCancel(null, reason);
  }

  public final void cancel(@NotNull Throwable cause, @NotNull String reason) {
    doCancel(cause, reason);
  }

  // true if canceled successfully
  private void doCancel(@Nullable Throwable cause, @NotNull String reason) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("doCancel(" + this +
                (reason.isEmpty() ? "" : ", reason: '" + reason + "'") +
                (cause == null ? "" : ", cause: " + ExceptionUtil.getThrowableText(cause)) + ")");
    }
    boolean wasCanceled;
    synchronized (getLock()) {
      wasCanceled = tryCancel(cause);
    }
    if (wasCanceled) { // call outside synchronized to avoid deadlock
      ProgressManager.getInstance().executeNonCancelableSection(() -> onCancelled(reason));
    }
  }

  @Override
  public final boolean isCanceled() {
    return super.isCanceled();
  }

  @Override
  public final void checkCanceled() {
    super.checkCanceled();
  }

  @Override
  protected @Nullable Throwable getCancellationTrace() {
    Throwable cause = myCancellationCause;
    return cause != null ? cause : super.getCancellationTrace();
  }

  @Override
  public final void start() {
    checkCanceled();
    assert !isRunning() : "running";
    mySpan = myTraceManager.spanBuilder("run daemon").startSpan();
    super.start();
  }


  /**
   * Please use the more structured {@link #runInDebugMode} instead
   */
  @TestOnly
  @ApiStatus.Internal
  public static void setDebug(boolean debug) {
    DaemonProgressIndicator.debug.set(debug ? 1 : 0);
  }
  @TestOnly
  static <E extends Throwable> void runInDebugMode(@NotNull ThrowableRunnable<E> runnable) throws E {
    try {
      DaemonProgressIndicator.debug.incrementAndGet();
      runnable.run();
    }
    finally {
      DaemonProgressIndicator.debug.decrementAndGet();
    }
  }

  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return System.identityHashCode(this) + (debug.get()>0 ? "; " + myTraceableDisposable.getStackTrace() + "\n;" : "") + (isCanceled() ? "X" : "V");
  }

  @Override
  public boolean isIndeterminate() {
    // to avoid silly exceptions "this progress is indeterminate" on storing/restoring wrapper states in JobLauncher
    return false;
  }
}
