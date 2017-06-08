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

import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class LogMessage extends AbstractMessage {
  private final Throwable myThrowable;
  private final String myHeader;
  private List<Attachment> myAttachments;

  LogMessage(@NotNull LoggingEvent event) {
    Throwable throwable = event.getThrowableInformation() == null ? null : event.getThrowableInformation().getThrowable();
    myThrowable = throwable == null ? null : ThrowableInterner.intern(throwable);

    myHeader =
      event.getMessage() == null || event.getMessage().toString().isEmpty() ?
      getThrowable().toString() : event.getMessage().toString();
  }

  LogMessage(@NotNull IdeaLoggingEvent event) {
    Throwable throwable = event.getThrowable();
    myThrowable = throwable == null ? null : ThrowableInterner.intern(throwable);

    String header = null;

    if (!StringUtil.isEmptyOrSpaces(event.getMessage())) {
      header = event.getMessage();
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
    return StringUtil.join(IdeaLogger.getThrowableRenderer().doRender(getThrowable()), Layout.LINE_SEP);
  }

  void addAttachment(@NotNull Attachment attachment) {
    if (myAttachments == null) {
      myAttachments = ContainerUtil.createLockFreeCopyOnWriteList();
    }
    myAttachments.add(attachment);
  }

  @NotNull
  @Override
  public List<Attachment> getAllAttachments() {
    return myAttachments != null ? myAttachments : Collections.emptyList();
  }
}
