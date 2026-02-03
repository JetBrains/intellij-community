// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performanceTests;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class DummyProjectInitializationDiagnosticService implements ProjectInitializationDiagnosticService {

  @Override
  public ActivityTracker registerBeginningOfInitializationActivity(@NotNull Supplier<@NotNull @NlsSafe String> debugMessageProducer) { return new MyActivityTracker(); }

  @Override
  public boolean isProjectInitializationAndIndexingFinished() {
    return true;
  }

  private static class MyActivityTracker implements ActivityTracker {
    @Override
    public void activityFinished() {
    }
  }
}
