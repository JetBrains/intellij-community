// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.diagnostic;

import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.helpers.SharedMetrics;

import java.util.concurrent.Semaphore;

public final class ExternalSystemSyncDiagnostic extends SharedMetrics {
  // Root span for the sync. Named that way for legacy metric name compatibility
  public static final String gradleSyncSpanName = "gradle.sync.duration";
  private static final Semaphore lock = new Semaphore(1);
  private static ExternalSystemSyncDiagnostic instance = null;

  private ExternalSystemSyncDiagnostic() { super(new Scope("external-system-sync", null)); }

  public static ExternalSystemSyncDiagnostic getInstance() {
    try {
      lock.acquire();
      if (instance != null) return instance;
      instance = new ExternalSystemSyncDiagnostic();
    }
    catch (InterruptedException e) {
      lock.release();
    }
    finally {
      lock.release();
    }

    return instance;
  }
}
