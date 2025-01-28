/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.database.util;

import com.intellij.database.DataGridBundle;
import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.connection.throwable.info.ThrowableInfoUtil;
import com.intellij.database.datagrid.GridUtilCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author gregsh
 */
public class ErrorHandler {
  private static final Logger LOG = Logger.getInstance(ErrorHandler.class);

  private final Set<String> myMessages = new HashSet<>();
  private final List<ErrorInfo.Fix> myFixes = new ArrayList<>();
  private final @NlsSafe StringBuilder myErrors = new StringBuilder();
  private boolean myTruncated;
  private int mySkippedErrors;

  public boolean hasErrors() {
    return !myErrors.isEmpty();
  }

  public @NotNull List<ErrorInfo.Fix> getFixes() {
    return myFixes;
  }

  public void add(@NotNull ErrorInfo errorInfo) {
    add(errorInfo, false);
  }

  public void add(@NotNull ErrorInfo errorInfo, boolean needToWarn) {
    addError(errorInfo.getMessage(), null);
    myFixes.addAll(ThrowableInfoUtil.getAllFixes(errorInfo));
    Throwable throwable = errorInfo.getOriginalThrowable();
    if (needToWarn && throwable != null) LOG.warn(throwable);
  }

  public boolean addError(@Nls @Nullable String message, @Nullable Throwable ex) {
    if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
    if (myTruncated) {
      mySkippedErrors++;
      return true;
    }
    String exMessage;
    if (ex != null) {
      String m = GridUtilCore.getLongMessage(ex);
      m = StringUtil.isNotEmpty(m) ? m : ex.getClass().getName();
      exMessage = m.equals(message) ? null : m;
    }
    else {
      exMessage = null;
    }

    // do not log similar errors
    if (message != null && !myMessages.add(message) ||
        exMessage != null && !myMessages.add(exMessage)) {
      mySkippedErrors++;
      return true;
    }
    if (ex != null) LOG.warn(ex);

    if (message != null) {
      appendMessage(message);
    }
    if (exMessage != null) {
      Throwable cause = ex.getCause();
      if (cause instanceof UnknownHostException) {
        appendMessage(DataGridBundle.message("host.0.is.unknown", cause.getMessage()));
      }
      appendMessage(StringUtil.replace(exMessage, "\n\n", ".\n"));
    }

    if (myErrors.length() > 10240) myTruncated = true;
    return true;
  }

  private void appendMessage(@Nls @NotNull String message) {
    String trimmed = message.trim();
    myErrors.append(trimmed);
    myErrors.append(trimmed.endsWith(".") ? "\n" : ".\n");
  }

  public @Nls String getSummary() {
    if (mySkippedErrors > 0) {
      myErrors.append(DataGridBundle.message("and.0.1.choice.0.more.1.duplicate.reports", mySkippedErrors, myTruncated ? 0 : 1));
      mySkippedErrors = 0;
    }
    return myErrors.toString();
  }

  public @Nls String getSummary(@Nls String alt) {
    return hasErrors() ? getSummary() : alt;
  }

  public void setCaption(@Nls String message) {
    myErrors.insert(0, message + "\n");
  }
}
