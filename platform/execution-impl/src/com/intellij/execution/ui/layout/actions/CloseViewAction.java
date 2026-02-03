// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CloseViewAction extends BaseViewAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(content));
    boolean unpinAction = isUnpinAction(e, content);
    Presentation presentation = e.getPresentation();
    if (unpinAction) {
      String text = RegistryManager.getInstance().is("ide.editor.tabs.interactive.pin.button") ?
                    TextWithMnemonic.parse(IdeBundle.message("action.unpin.tab")).dropMnemonic(true).getText() :
                    "";
      presentation.setText(text);
    }
    presentation.setIcon(unpinAction ? AllIcons.Actions.PinTab : AllIcons.Actions.Close);
    presentation.setHoveredIcon(unpinAction ? AllIcons.Actions.PinTab : AllIcons.Actions.CloseHovered);
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
