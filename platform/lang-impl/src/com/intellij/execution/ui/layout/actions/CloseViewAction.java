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

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class CloseViewAction extends BaseViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(context, content, e.getPlace()));
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.getContentManager().removeContent(content[0], context.isToDisposeRemovedContent());
  }

  public static boolean isEnabled(ViewContext context, Content[] content, String place) {
    return content.length == 1 && content[0].isCloseable();
  }
  
}