/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PausesStat;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActionUtil {
  @NonNls private static final String WAS_ENABLED_BEFORE_DUMB = "WAS_ENABLED_BEFORE_DUMB";
  @NonNls public static final String WOULD_BE_ENABLED_IF_NOT_DUMB_MODE = "WOULD_BE_ENABLED_IF_NOT_DUMB_MODE";
  @NonNls private static final String WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE = "WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE";

  private ActionUtil() {
  }

  public static void showDumbModeWarning(@NotNull AnActionEvent... events) {
    Project project = null;
    List<String> actionNames = new ArrayList<String>();
    for (final AnActionEvent event : events) {
      final String s = event.getPresentation().getText();
      if (StringUtil.isNotEmpty(s)) {
        actionNames.add(s);
      }

      final Project _project = CommonDataKeys.PROJECT.getData(event.getDataContext());
      if (_project != null && project == null) {
        project = _project;
      }
    }

    if (project == null) {
      return;
    }

    DumbService.getInstance(project).showDumbModeNotification(getActionUnavailableMessage(actionNames));
  }

  @NotNull
  private static String getActionUnavailableMessage(@NotNull List<String> actionNames) {
    String message;
    final String beAvailableUntil = " available while " + ApplicationNamesInfo.getInstance().getProductName() + " is updating indices";
    if (actionNames.isEmpty()) {
      message = "This action is not" + beAvailableUntil;
    }
    else if (actionNames.size() == 1) {
      message = "'" + actionNames.get(0) + "' action is not" + beAvailableUntil;
    }
    else {
      message = "None of the following actions are" + beAvailableUntil + ": " + StringUtil.join(actionNames, ", ");
    }
    return message;
  }

  @NotNull
  public static String getUnavailableMessage(@NotNull String action, boolean plural) {
    return action + (plural ? " are" : " is")
           + " not available while " + ApplicationNamesInfo.getInstance().getProductName() + " is updating indices";
  }

  public static final PausesStat ACTION_UPDATE_PAUSES = new PausesStat("AnAction.update()");
  private static int insidePerformDumbAwareUpdate;
  /**
   * @param action action
   * @param e action event
   * @param beforeActionPerformed whether to call
   * {@link AnAction#beforeActionPerformedUpdate(AnActionEvent)}
   * or
   * {@link AnAction#update(AnActionEvent)}
   * @return true if update tried to access indices in dumb mode
   */
  public static boolean performDumbAwareUpdate(@NotNull AnAction action, @NotNull AnActionEvent e, boolean beforeActionPerformed) {
    final Presentation presentation = e.getPresentation();
    final Boolean wasEnabledBefore = (Boolean)presentation.getClientProperty(WAS_ENABLED_BEFORE_DUMB);
    final boolean dumbMode = isDumbMode(CommonDataKeys.PROJECT.getData(e.getDataContext()));
    if (wasEnabledBefore != null && !dumbMode) {
      presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, null);
      presentation.setEnabled(wasEnabledBefore.booleanValue());
      presentation.setVisible(true);
    }
    final boolean enabledBeforeUpdate = presentation.isEnabled();

    final boolean notAllowed = dumbMode && !action.isDumbAware();

    if (insidePerformDumbAwareUpdate++ == 0) {
      ACTION_UPDATE_PAUSES.started();
    }
    try {
      if (beforeActionPerformed) {
        action.beforeActionPerformedUpdate(e);
      }
      else {
        action.update(e);
      }
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, notAllowed && presentation.isEnabled());
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, notAllowed && presentation.isVisible());
    }
    catch (IndexNotReadyException e1) {
      if (notAllowed) {
        return true;
      }
      throw e1;
    }
    finally {
      if (--insidePerformDumbAwareUpdate == 0) {
        ACTION_UPDATE_PAUSES.finished(presentation.getText()+" action update ("+action.getClass()+")");
      }
      if (notAllowed) {
        if (wasEnabledBefore == null) {
          presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, enabledBeforeUpdate);
        }
        presentation.setEnabled(false);
      }
    }
    
    return false;
  }

  /**
   * @return whether a dumb mode is in progress for the passed project or, if the argument is null, for any open project.
   * @see DumbService
   */
  public static boolean isDumbMode(@Nullable Project project) {
    if (project != null) {
      return DumbService.getInstance(project).isDumb();
    }
    for (Project proj : ProjectManager.getInstance().getOpenProjects()) {
      if (DumbService.getInstance(proj).isDumb()) {
        return true;
      }
    }
    return false;

  }

  public static boolean lastUpdateAndCheckDumb(AnAction action, AnActionEvent e, boolean visibilityMatters) {
    performDumbAwareUpdate(action, e, true);

    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null && DumbService.getInstance(project).isDumb() && !action.isDumbAware()) {
      if (Boolean.FALSE.equals(e.getPresentation().getClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE))) {
        return false;
      }
      if (visibilityMatters && Boolean.FALSE.equals(e.getPresentation().getClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE))) {
        return false;
      }

      showDumbModeWarning(e);
      return false;
    }

    if (!e.getPresentation().isEnabled()) {
      return false;
    }
    if (visibilityMatters && !e.getPresentation().isVisible()) {
      return false;
    }

    return true;
  }

  public static void performActionDumbAware(AnAction action, AnActionEvent e) {
    try {
      action.actionPerformed(e);
    }
    catch (IndexNotReadyException e1) {
      showDumbModeWarning(e);
    }
  }

  @NotNull
  public static List<AnAction> getActions(@NotNull JComponent component) {
    Object property = component.getClientProperty(AnAction.ourClientProperty);
    //noinspection unchecked
    return property == null ? Collections.<AnAction>emptyList() : (List<AnAction>)property;
  }

  public static void registerForEveryKeyboardShortcut(@NotNull JComponent component,
                                                      @NotNull ActionListener action,
                                                      @NotNull ShortcutSet shortcuts) {
    for (Shortcut shortcut : shortcuts.getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut ks = (KeyboardShortcut)shortcut;
        KeyStroke first = ks.getFirstKeyStroke();
        KeyStroke second = ks.getSecondKeyStroke();
        if (second == null) {
          component.registerKeyboardAction(action, first, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
      }
    }
  }
}
