/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.Comparator;

public abstract class ActionManagerEx extends ActionManager {
  public static ActionManagerEx getInstanceEx() {
    return (ActionManagerEx)getInstance();
  }

  @NotNull
  public abstract ActionToolbar createActionToolbar(String place, @NotNull ActionGroup group, boolean horizontal, boolean decorateButtons);

  public abstract void fireBeforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event);

  public abstract void fireAfterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event);


  public abstract void fireBeforeEditorTyping(char c, DataContext dataContext);

  /**
   * For logging purposes
   */

  public abstract String getLastPreformedActionId();

  public abstract String getPrevPreformedActionId();

  /**
   * Comparator compares action ids (String) on order of action registration.
   *
   * @return a negative integer if action that corresponds to the first id was registered earlier than the action that corresponds
   *  to the second id; zero if both ids are equal; a positive number otherwise.
   */
  @NotNull
  public abstract Comparator<String> getRegistrationOrderComparator();

  /**
   * Similar to {@link KeyStroke#getKeyStroke(String)} but allows keys in lower case.
   * <p/>
   * I.e. "control x" is accepted and interpreted as "control X".
   *
   * @return null if string cannot be parsed.
   */
  @Nullable
  public static KeyStroke getKeyStroke(String s) {
    KeyStroke result = null;
    try {
      result = KeyStroke.getKeyStroke(s);
    }
    catch (Exception ex) {
      //ok
    }
    if (result == null && s != null && s.length() >= 2 && s.charAt(s.length() - 2) == ' ') {
      try {
        String s1 = s.substring(0, s.length() - 1) + Character.toUpperCase(s.charAt(s.length() - 1));
        result = KeyStroke.getKeyStroke(s1);
      }
      catch (Exception ignored) {
      }
    }
    return result;
  }


  @NotNull
  public abstract String[] getPluginActions(@NotNull PluginId pluginId);

  public abstract void queueActionPerformedEvent(final AnAction action, DataContext context, AnActionEvent event);

  public abstract boolean isActionPopupStackEmpty();

  public abstract boolean isTransparentOnlyActionsUpdateNow();

  public void fireBeforeActionPerformed(String actionId, InputEvent event) {
    final AnAction action = getAction(actionId);
    if (action != null) {
      AnActionEvent e = AnActionEvent.createFromAnAction(action, event, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext());
      fireBeforeActionPerformed(action, DataManager.getInstance().getDataContext(), e);
    }
  }
}

