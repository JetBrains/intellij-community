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

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PausesStat;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.ex.ActionUtil");
  @NonNls private static final String WAS_ENABLED_BEFORE_DUMB = "WAS_ENABLED_BEFORE_DUMB";
  @NonNls public static final String WOULD_BE_ENABLED_IF_NOT_DUMB_MODE = "WOULD_BE_ENABLED_IF_NOT_DUMB_MODE";
  @NonNls private static final String WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE = "WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE";

  private ActionUtil() {
  }

  public static void showDumbModeWarning(@NotNull AnActionEvent... events) {
    Project project = null;
    List<String> actionNames = new ArrayList<>();
    for (final AnActionEvent event : events) {
      final String s = event.getPresentation().getText();
      if (StringUtil.isNotEmpty(s)) {
        actionNames.add(s);
      }

      final Project _project = event.getProject();
      if (_project != null && project == null) {
        project = _project;
      }
    }

    if (project == null) {
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Showing dumb mode warning for " + Arrays.asList(events), new Throwable());
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
  public static boolean performDumbAwareUpdate(boolean isInModalContext, @NotNull AnAction action, @NotNull AnActionEvent e, boolean beforeActionPerformed) {
    final Presentation presentation = e.getPresentation();
    final Boolean wasEnabledBefore = (Boolean)presentation.getClientProperty(WAS_ENABLED_BEFORE_DUMB);
    final boolean dumbMode = isDumbMode(e.getProject());
    if (wasEnabledBefore != null && !dumbMode) {
      presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, null);
      presentation.setEnabled(wasEnabledBefore.booleanValue());
      presentation.setVisible(true);
    }
    final boolean enabledBeforeUpdate = presentation.isEnabled();

    final boolean notAllowed = dumbMode && !action.isDumbAware() || (Registry.is("actionSystem.honor.modal.context") && isInModalContext && !action.isEnabledInModalContext());

    String description = presentation.getText() + " action update (" + action.getClass() + ")";
    if (insidePerformDumbAwareUpdate++ == 0) {
      ActionPauses.STAT.started(description);
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
        ActionPauses.STAT.finished(description);
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

  @Deprecated
  // Use #performDumbAwareUpdate with isModalContext instead
  public static boolean performDumbAwareUpdate(@NotNull AnAction action, @NotNull AnActionEvent e, boolean beforeActionPerformed) {
    return performDumbAwareUpdate(false, action, e, beforeActionPerformed);
  }

  public static class ActionPauses {
    public static final PausesStat STAT = new PausesStat("AnAction.update()");
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
    performDumbAwareUpdate(false, action, e, true);

    final Project project = e.getProject();
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
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          action.actionPerformed(e);
        }
        catch (IndexNotReadyException ex) {
          LOG.info(ex);
          showDumbModeWarning(e);
        }
      }

      @Override
      public String toString() {
        return action + " of " + action.getClass();
      }
    };

    if (action.startInTransaction()) {
      TransactionGuard.getInstance().submitTransactionAndWait(runnable);
    } else {
      runnable.run();
    }
  }

  @NotNull
  public static List<AnAction> getActions(@NotNull JComponent component) {
    return ContainerUtil.notNullize(UIUtil.getClientProperty(component, AnAction.ACTIONS_KEY));
  }

  public static void clearActions(@NotNull JComponent component) {
    UIUtil.putClientProperty(component, AnAction.ACTIONS_KEY, null);
  }

  public static void copyRegisteredShortcuts(@NotNull JComponent to, @NotNull JComponent from) {
    for (AnAction anAction : getActions(from)) {
      anAction.registerCustomShortcutSet(anAction.getShortcutSet(), to);
    }
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

  public static void recursiveRegisterShortcutSet(@NotNull ActionGroup group,
                                                  @NotNull JComponent component,
                                                  @Nullable Disposable parentDisposable) {
    for (AnAction action : group.getChildren(null)) {
      if (action instanceof ActionGroup) {
        recursiveRegisterShortcutSet(((ActionGroup)action), component, parentDisposable);
      }
      action.registerCustomShortcutSet(component, parentDisposable);
    }
  }

  /**
   * Convenience method for copying properties from a registered action
   *
   * @param actionId action id
   */
  public static AnAction copyFrom(@NotNull AnAction action, @NotNull String actionId) {
    action.copyFrom(ActionManager.getInstance().getAction(actionId));
    return action;
  }

  /**
   * Convenience method for merging not null properties from a registered action
   *
   * @param action action to merge to
   * @param actionId action id to merge from
   */
  public static AnAction mergeFrom(@NotNull AnAction action, @NotNull String actionId) {
    //noinspection UnnecessaryLocalVariable
    AnAction a1 = action;
    AnAction a2 = ActionManager.getInstance().getAction(actionId);
    Presentation p1 = a1.getTemplatePresentation();
    Presentation p2 = a2.getTemplatePresentation();
    p1.setIcon(ObjectUtils.chooseNotNull(p1.getIcon(), p2.getIcon()));
    p1.setDisabledIcon(ObjectUtils.chooseNotNull(p1.getDisabledIcon(), p2.getDisabledIcon()));
    p1.setSelectedIcon(ObjectUtils.chooseNotNull(p1.getSelectedIcon(), p2.getSelectedIcon()));
    p1.setHoveredIcon(ObjectUtils.chooseNotNull(p1.getHoveredIcon(), p2.getHoveredIcon()));
    if (StringUtil.isEmpty(p1.getText())) {
      p1.setText(p2.getTextWithMnemonic(), p2.getDisplayedMnemonicIndex() >= 0);
    }
    p1.setDescription(ObjectUtils.chooseNotNull(p1.getDescription(), p2.getDescription()));
    ShortcutSet ss1 = a1.getShortcutSet();
    if (ss1 == null || ss1 == CustomShortcutSet.EMPTY) {
      a1.copyShortcutFrom(a2);
    }
    return a1;
  }

  public static void invokeAction(@NotNull AnAction action, @Nullable InputEvent inputEvent, @NotNull Component component, @NotNull String place, @Nullable Runnable onDone) {
    Presentation presentation = action.getTemplatePresentation().clone();
    AnActionEvent event = new AnActionEvent(inputEvent, DataManager.getInstance().getDataContext(component),
                                            place,
                                            presentation,
                                            ActionManager.getInstance(),
                                            0);
    performDumbAwareUpdate(false, action, event, true);
    if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
      action.actionPerformed(event);
      if (onDone != null) {
        onDone.run();
      }
    }
  }

  @NotNull
  public static ActionListener createActionListener(@NotNull String actionId, @NotNull Component component, @NotNull String place) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action == null) {
          LOG.warn("Can not find action by id " + actionId);
          return;
        }
        invokeAction(action, null, component, place, null);
      }
    };
  }
}
