// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class GroupedLogMessage extends AbstractMessage {
  private static final String INDUCED_STACKTRACES_ATTACHMENT = "induced.txt";
  private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

  private final List<AbstractMessage> myMessages;
  private AbstractMessage myProxy;

  public GroupedLogMessage(List<AbstractMessage> messages) {
    myMessages = messages;
  }

  public List<AbstractMessage> getMessages() {
    return myMessages;
  }

  @Override
  public @NotNull Throwable getThrowable() {
    return myMessages.get(0).getThrowable();
  }

  @Override
  public @NotNull String getThrowableText() {
    StringBuilder result = new StringBuilder();
    for (AbstractMessage each : myMessages) {
      if (result.length() > 0) result.append("\n\n\n");
      result.append(each.getThrowableText());
    }
    return result.toString();
  }

  @Override
  public @Nullable String getMessage() {
    return myMessages.get(0).getMessage();
  }

  @Override
  public @NotNull List<Attachment> getAllAttachments() {
    return ContainerUtil.concat(getMessages(), message -> message.getAllAttachments());
  }

  @Override
  public void setRead(boolean isRead) {
    super.setRead(isRead);
    for (AbstractMessage message : myMessages) {
      message.setRead(isRead);
    }
  }

  @Override
  public void setAssigneeId(@Nullable Integer assigneeId) {
    super.setAssigneeId(assigneeId);
    for (AbstractMessage message : myMessages) {
      message.setAssigneeId(assigneeId);
    }
  }

  /**
   * Proxies this message for IdeErrorsDialog.
   */
  AbstractMessage getProxyMessage() {
    if (myProxy == null) {
      AbstractMessage mainCause = myMessages.get(0);

      List<Attachment> attachments = new ArrayList<>(mainCause.getAllAttachments());
      StringBuilder stacktraces = new StringBuilder("Following exceptions happened soon after this one, most probably they are induced.");
      for (AbstractMessage each : myMessages) {
        if (each != mainCause) {
          stacktraces.append("\n\n\n").append(TIMESTAMP_FORMAT.format(each.getDate())).append('\n');
          if (!StringUtil.isEmptyOrSpaces(each.getMessage())) stacktraces.append(each.getMessage()).append('\n');
          stacktraces.append(each.getThrowableText());
        }
      }
      attachments.add(new Attachment(INDUCED_STACKTRACES_ATTACHMENT, stacktraces.toString()));

      myProxy = new ProxyLogMessage(mainCause.getThrowable(), mainCause.getMessage(), attachments, this);
    }

    return myProxy;
  }

  private static class ProxyLogMessage extends LogMessage {
    private final GroupedLogMessage myOriginal;

    private ProxyLogMessage(Throwable throwable, String message, List<Attachment> attachments, GroupedLogMessage original) {
      super(throwable, message, attachments);
      myOriginal = original;
    }

    @Override
    public void setRead(boolean isRead) {
      super.setRead(isRead);
      myOriginal.setRead(isRead);
    }
  }
}