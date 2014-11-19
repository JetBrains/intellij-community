/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.project.DumbAwareAction;

import java.awt.event.InputEvent;

@SuppressWarnings({"HardCodedStringLiteral"})
public class DropAnErrorWithAttachmentsAction extends DumbAwareAction {
  public DropAnErrorWithAttachmentsAction() {
    super("Drop An Error With Attachments", "Hold down SHIFT for multiple attachments", null);
  }

  public void actionPerformed(AnActionEvent e) {
    final boolean multipleAttachments = (e.getModifiers() & InputEvent.SHIFT_MASK) != 0;
    Attachment[] attachments;
    if (multipleAttachments) {
      attachments = new Attachment[]{new Attachment("first.txt", "first content"), new Attachment("second.txt", "second content")};
    }
    else {
      attachments = new Attachment[]{new Attachment("attachment.txt", "content")};
    }
    IdeaLoggingEvent test = LogMessageEx.createEvent("test", "test details", attachments);
    throw new LogEventException(test);
    //Logger.getInstance("test (with attachments)").error(test);
  }
}
