// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class TestDialogManager {
  private static TestDialog implementation;
  private static TestInputDialog inputImplementation = TestInputDialog.DEFAULT;

  @TestOnly
  public static TestDialog setTestDialog(@Nullable TestDialog newValue) {
    checkUnitTestMode();
    TestDialog oldValue = implementation;
    implementation = newValue;
    return oldValue;
  }

  @TestOnly
  public static TestDialog setTestDialog(@Nullable TestDialog newValue, @NotNull Disposable disposable) {
    TestDialog oldValue = setTestDialog(newValue);
    Disposer.register(disposable, () -> implementation = oldValue);
    return oldValue;
  }

  public static @NotNull TestDialog getTestImplementation() {
    TestDialog result = implementation;
    return result == null ? TestDialog.DEFAULT : result;
  }

  public static TestInputDialog getTestInputImplementation() {
    TestInputDialog result = inputImplementation;
    return result == null ? TestInputDialog.DEFAULT : result;
  }

  @TestOnly
  public static TestInputDialog setTestInputDialog(@Nullable TestInputDialog newValue) {
    checkUnitTestMode();
    TestInputDialog oldValue = inputImplementation;
    inputImplementation = newValue;
    return oldValue;
  }

  private static void checkUnitTestMode() {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      Logger.getInstance(Messages.class).assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
  }
}
