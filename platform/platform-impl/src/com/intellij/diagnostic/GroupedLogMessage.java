// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GroupedLogMessage extends AbstractMessage {
  private final List<AbstractMessage> myMessages;

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
}