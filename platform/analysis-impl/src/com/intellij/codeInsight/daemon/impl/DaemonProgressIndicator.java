/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.TraceableDisposable;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class DaemonProgressIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
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
    cancel("daemon progress cancelled with no specified reason");
  }

  public final void cancel(@NotNull String reason) {
    doCancel(null, reason);
  }

  public final void cancel(@NotNull Throwable cause, @NotNull String reason) {
    doCancel(cause, reason);
  }

  private void doCancel(@Nullable Throwable cause, @NotNull String reason) {
    if (tryCancel()) {
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

  @Nullable
  @Override
  protected Throwable getCancellationTrace() {
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
    return super.toString() + (debug ? "; "+myTraceableDisposable.getStackTrace()+"\n;" : "");
  }

  @Override
  public boolean isIndeterminate() {
    // to avoid silly exceptions "this progress is indeterminate" on storing/restoring wrapper states in JobLauncher
    return false;
  }

  /**
   * @deprecated does nothing, use {@link #cancel()} instead
   */
  @Deprecated(forRemoval = true)
  public void dispose() {
  }
}
