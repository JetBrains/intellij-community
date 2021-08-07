// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.TestOnly;

public final class ApplicationManagerEx extends ApplicationManager {
  public static final String IS_INTERNAL_PROPERTY = "idea.is.internal";

  private static volatile boolean inStressTest;

  public static ApplicationEx getApplicationEx() {
    return (ApplicationEx)ourApplication;
  }

  public static boolean isInStressTest() {
    return inStressTest;
  }

  @TestOnly
  public static void setInStressTest(boolean value) {
    inStressTest = value;
  }
}