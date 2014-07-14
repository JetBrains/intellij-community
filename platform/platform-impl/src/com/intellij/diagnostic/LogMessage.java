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

public class LogMessage extends AbstractMessage {
  private final Throwable myThrowable;
  private final String myHeader;

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
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

    String header = null;

    if (!StringUtil.isEmptyOrSpaces(aEvent.getMessage())) {
      header = aEvent.getMessage();
    }

    if (myThrowable != null) {
      String message = myThrowable.getMessage();
      if (StringUtil.isNotEmpty(message) && (header == null || !header.startsWith(message))) {
        if (header != null) {
          if (header.endsWith(":")) header += " ";
          else if (!header.endsWith(": ")) header += ": ";
          header += message;
        }
        else {
          header = message;
        }
      }
    }

    if (header == null) {
      header = "No message";
    }

    myHeader = header;
  }

  @Override
  public Throwable getThrowable() {
    return myThrowable;
  }

  @Override
  public String getMessage() {
    return myHeader;
  }

  @Override
  public String getThrowableText() {
    return StringUtil.getThrowableText(getThrowable());
  }
}
