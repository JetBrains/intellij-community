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

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

public class CloseAllUnpinnedViewsAction extends CloseViewsActionBase {
  @Override
  public boolean isEnabled(ViewContext context, Content[] selectedContents, String place) {
    for (Content content : context.getContentManager().getContents()) {
      if (content.isPinned()) return super.isEnabled(context, selectedContents, place);
    }
    return false;
  }

  @Override
  protected boolean isAccepted(@NotNull Content c, Content @NotNull [] selectedContents) {
    return !c.isPinned();
  }
}