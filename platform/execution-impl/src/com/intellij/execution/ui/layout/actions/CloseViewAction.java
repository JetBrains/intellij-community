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
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.content.Content;

public class CloseViewAction extends BaseViewAction {

  @Override
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(content));
    boolean unpinAction = isUnpinAction(e, content);
    if (unpinAction) {
      if (!Registry.get("ide.editor.tabs.interactive.pin.button").asBoolean()) {
        e.getPresentation().setText("");
      }
      else {
        e.getPresentation().setText(TextWithMnemonic.parse(IdeBundle.message("action.unpin.tab")).dropMnemonic(true).getText());
      }
    }
    e.getPresentation().setIcon(unpinAction ? AllIcons.Actions.PinTab : AllIcons.Actions.Close);
    e.getPresentation().setHoveredIcon(unpinAction ? AllIcons.Actions.PinTab : AllIcons.Actions.CloseHovered);
  }

  @Override
  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
   if (isUnpinAction(e, content)) {
     if (Registry.get("ide.editor.tabs.interactive.pin.button").asBoolean()) {
       content[0].setPinned(false);
     }
     return;
   }
   perform(context, content[0]);
  }

  public static boolean perform(ViewContext context, Content content) {
    return context.getContentManager().removeContent(content, context.isToDisposeRemovedContent());
  }

  public static boolean isEnabled(Content[] content) {
    return content.length == 1 && content[0].isCloseable();
  }

  private static boolean isUnpinAction(AnActionEvent e, Content[] content) {
    return content.length == 1 && content[0].isPinnable() && content[0].isPinned() && !ViewContext.isPopupPlace(e.getPlace());
  }
}
