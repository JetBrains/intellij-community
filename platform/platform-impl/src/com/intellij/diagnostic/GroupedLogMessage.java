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

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class GroupedLogMessage extends AbstractMessage {

  private final List<AbstractMessage> myMessages;

  public GroupedLogMessage(List<AbstractMessage> messages) {
    myMessages = messages;
  }

  public List<AbstractMessage> getMessages() {
    return myMessages;
  }

  public String getThrowableText() {
    StringBuilder result = new StringBuilder();
    for (AbstractMessage each : myMessages) {
      result.append(each.getThrowableText()).append("\n\n\n");
    }
    return result.toString();
  }

  @Override
  public void setRead(boolean aReadFlag) {
    for (AbstractMessage message : myMessages) {
      message.setRead(aReadFlag);
    }
    super.setRead(aReadFlag);
  }

  public Throwable getThrowable() {
    return myMessages.get(0).getThrowable();
  }

  public String getMessage() {
    return myMessages.get(0).getMessage();
  }

  @Override
  public void setAssigneeId(Integer assigneeId) {
    for (AbstractMessage message : myMessages) {
      message.setAssigneeId(assigneeId);
    }
    super.setAssigneeId(assigneeId);
  }

  @NotNull
  @Override
  public List<Attachment> getAllAttachments() {
    return ContainerUtil.concat(getMessages(), new Function<AbstractMessage, Collection<? extends Attachment>>() {
      @Override
      public Collection<? extends Attachment> fun(AbstractMessage message) {
        return message.getAllAttachments();
      }
    });
  }
}
