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
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class DaemonProgressIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
  private static final Logger LOG = Logger.getInstance(DaemonProgressIndicator.class);
  private static boolean debug;
  private final TraceableDisposable myTraceableDisposable = new TraceableDisposable(debug);
  private volatile Throwable myCancellationCause;
  private volatile Span mySpan;
  private final IJTracer myTraceManager = TelemetryManager.Companion.getInstance().getTracer(new Scope("daemon", null));

  @Override
  public final void stop() {
    boolean cancelled = false;
    synchronized (getLock()) {
      super.stop();
      if (tryCancel()) {
        cancelled = true;
      }
    }
    if (cancelled) {
      onStop();
    }
  }

  // return true if was stopped
  void stopIfRunning() {
    synchronized (getLock()) {
      if(mySpan != null) {
        mySpan.end();
      }
      if (isRunning()) {
        stop();
        return;
      }
      cancel();
    }
  }

  private boolean tryCancel() {
    synchronized (getLock()) {
      if (!isCanceled()) {
        myTraceableDisposable.kill("Daemon Progress Canceled");
        super.cancel();
        return true;
      }
    }
    return false;
  }

  protected void onCancelled(@NotNull String reason) { }

  protected void onStop() { }

  @Override
  public final void cancel() {
    Throwable cause = LOG.isDebugEnabled() ? new Throwable() : null;
    doCancel(cause, "cancel() was called");
  }

  public final void cancel(@NotNull String reason) {
    doCancel(null, reason);
  }

  public final void cancel(@NotNull Throwable cause, @NotNull String reason) {
    doCancel(cause, reason);
  }

  private void doCancel(@Nullable Throwable cause, @NotNull String reason) {
    if (tryCancel()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("doCancel(" + this +
                  (reason.isEmpty() ? "" : ", reason: '" + reason + "'") +
                  (cause == null ? "" : ", cause: " + ExceptionUtil.getThrowableText(cause)) + ")");
      }
      myCancellationCause = cause;
      if (cause != null) {
        myTraceableDisposable.killExceptionally(cause);
      }
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

  @TestOnly
  public static void setDebug(boolean debug) {
    DaemonProgressIndicator.debug = debug;
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
    return System.identityHashCode(this) + (debug ? "; " + myTraceableDisposable.getStackTrace() + "\n;" : "") + " "+(isCanceled() ? "X" : "V");
  }

  @Override
  public boolean isIndeterminate() {
    // to avoid silly exceptions "this progress is indeterminate" on storing/restoring wrapper states in JobLauncher
    return false;
  }
}
