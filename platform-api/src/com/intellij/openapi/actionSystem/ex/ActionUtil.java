package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.event.InputEvent;

public class ActionUtil {

  private ActionUtil() {
  }

  public static void execute(@NonNls @NotNull String actionID, @NotNull InputEvent intputEvent, @Nullable Component contextComponent, @NotNull String place, int modifiers) {
    final ActionManager manager = ActionManager.getInstance();
    final AnAction action = manager.getAction(actionID);
    assert action != null : actionID;

    final DataManager dataManager = DataManager.getInstance();
    final DataContext context = contextComponent != null ? dataManager.getDataContext(contextComponent) : dataManager.getDataContext();

    final Presentation presentation = (Presentation)action.getTemplatePresentation().clone();

    final AnActionEvent event = new AnActionEvent(intputEvent, context, place, presentation, manager, modifiers);

    action.update(event);

    if (event.getPresentation().isEnabled()) {
      action.beforeActionPerformedUpdate(event);
      action.actionPerformed(event);
    }
  }
}