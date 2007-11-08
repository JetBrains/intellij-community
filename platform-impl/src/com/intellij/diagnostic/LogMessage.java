/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NonNls;

public class LogMessage extends AbstractMessage {

  @NonNls static final String NO_MESSAGE = "No message";

  private String myHeader = NO_MESSAGE;
  private final Throwable myThrowable;

  public LogMessage(LoggingEvent aEvent) {
    super();

    myThrowable = aEvent.getThrowableInformation() == null ? null : aEvent.getThrowableInformation().getThrowable();

    if (aEvent.getMessage() == null || aEvent.getMessage().toString().length() == 0) {
      myHeader = getThrowable().toString();
    }
    else {
      myHeader = aEvent.getMessage().toString();
    }
  }

  public LogMessage(IdeaLoggingEvent aEvent) {
    super();

    myThrowable = aEvent.getThrowable();

    if (StringUtil.isNotEmpty(aEvent.getMessage())) {
      myHeader = aEvent.getMessage();
    }

    if (myThrowable != null && StringUtil.isNotEmpty(myThrowable.getMessage())) {
      if (myHeader != NO_MESSAGE ) {
        if (!myHeader.endsWith(": ") && !myHeader.endsWith(":")) {
          myHeader += ": ";
        }
        myHeader += myThrowable.getMessage();
      }
      else {
        myHeader = myThrowable.getMessage();
      }
    }
  }

  public Throwable getThrowable() {
    return myThrowable;
  }

  public String getMessage() {
    return myHeader;
  }

  public String getThrowableText() {
    return StringUtil.getThrowableText(getThrowable());
  }

}
