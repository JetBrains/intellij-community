// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

public final class ValidationResult {
  private final String myMessage;
  private final String myTitle;
  private final Runnable myQuickFix;

  public ValidationResult(final String message, final String title, final Runnable quickFix) {
    myMessage = message;
    myTitle = title;
    myQuickFix = quickFix;
  }

  public String getMessage() {
    return myMessage;
  }

  public String getTitle() {
    return myTitle;
  }

  public Runnable getQuickFix() {
    return myQuickFix;
  }
}
