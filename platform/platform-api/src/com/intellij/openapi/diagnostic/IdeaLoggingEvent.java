// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author kir
 */
public class IdeaLoggingEvent {
  private final String myMessage;
  private final Throwable myThrowable;
  private final Object myData;

  public IdeaLoggingEvent(String message, Throwable throwable) {
    this(message, throwable, null);
  }

  public IdeaLoggingEvent(String message, Throwable throwable, Object data) {
    myMessage = message;
    myThrowable = throwable;
    myData = data;
  }

  public String getMessage() {
    return myMessage;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }

  public String getThrowableText() {
    return myThrowable != null ? StringUtil.getThrowableText(myThrowable) : "";
  }

  @Nullable
  public Object getData() {
    return myData;
  }

  @Override
  public String toString() {
    return "IdeaLoggingEvent[message=" + myMessage + ", throwable=" + getThrowableText() + "]";
  }
}