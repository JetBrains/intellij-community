/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diagnostic;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author kir
 */
public class IdeaLoggingEvent {
  private final String myMessage;
  private final Throwable myThrowable;

  public IdeaLoggingEvent(String message, Throwable throwable) {
    myMessage = message;
    myThrowable = throwable;
  }

  public String getMessage() {
    return myMessage;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }

  public String getThrowableText() {
    if (myThrowable == null) return "";
    
    StringWriter stringWriter = new StringWriter();
    myThrowable.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.getBuffer().toString();
  }

  public String toString() {
    return "IdeaLoggingEvent[message=" + myMessage + ", throwable=" + getThrowableText() + "]";
  }
}
