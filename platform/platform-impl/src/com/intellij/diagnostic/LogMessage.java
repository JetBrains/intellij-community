/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
      if (!myHeader.equals(NO_MESSAGE)) {
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
