// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PausesStat;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionUtil {
  private static final Logger LOG = Logger.getInstance(ActionUtil.class);
  @NonNls private static final String WAS_ENABLED_BEFORE_DUMB = "WAS_ENABLED_BEFORE_DUMB";
  @NonNls public static final String WOULD_BE_ENABLED_IF_NOT_DUMB_MODE = "WOULD_BE_ENABLED_IF_NOT_DUMB_MODE";
  @NonNls private static final String WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE = "WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE";
  @NonNls private static final Key<ActionUpdateData> ACTION_UPDATE_DATA = Key.create("ACTION_UPDATE_DATA");

  private ActionUtil() {
  }

  public static void showDumbModeWarning(AnActionEvent @NotNull ... events) {
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
    if (actionNames.isEmpty()) {
      message = getUnavailableMessage("This action", false);
    }
    else if (actionNames.size() == 1) {
      message = getUnavailableMessage("'" + actionNames.get(0) + "'", false);
    }
    else {
      message = getUnavailableMessage("None of the following actions", true) +
                                       ": " + StringUtil.join(actionNames, ", ");
    }
    return message;
  }

  @NotNull
  public static String getUnavailableMessage(@NotNull String action, boolean plural) {
    return action + (plural ? " are" : " is")
           + " not available while " + ApplicationNamesInfo.getInstance().getProductName() + " is updating indices";
  }

  /**
   * Calculates time spent for update,
   * remember average time (with exponential smoothing) and caches update results inside action.getTemplatePresentation().getClientProperty(ACTION_UPDATE_DATA),
   * if average time is quite big then skip update invocation and use cached presentation.
   * @param forceUseCached use cached results for slow actions if presented (relax time doesn't take into account)
   */
  public static void performFastUpdate(boolean isInModalContext, @NotNull AnAction action, @NotNull AnActionEvent event, boolean forceUseCached) {
    final Presentation templatePresentation = action.getTemplatePresentation();
    ActionUpdateData ud = templatePresentation.getClientProperty(ACTION_UPDATE_DATA);
    if (ud == null)
      templatePresentation.putClientProperty(ACTION_UPDATE_DATA, ud = new ActionUpdateData());

    final boolean isSlow = ud.averageUpdateDurationMs > 10;// empiric val: 10 ms
    final long startTimeNs = System.nanoTime();
    final long relaxMs = Math.min(ud.averageUpdateDurationMs*100, 10000); // empiric vals: min 1 sec, max 10 sec
    if (isSlow && ud.lastUpdateEvent != null && (forceUseCached || (startTimeNs - ud.lastUpdateTimeNs) / 1000000L < relaxMs)) {
      // System.out.println("use cached presentation for action '" + String.valueOf(action) + "', averageUpdateDuration=" + ud.averageUpdateDurationMs + " ms, " + (startTimeNs - ud.lastUpdateTimeNs)/1000000l + " ms elapsed from last update");
      event.getPresentation().copyFrom(ud.lastUpdateEvent.getPresentation());
      return;
    }

    performDumbAwareUpdate(isInModalContext, action, event, false);
    final long finishUpdateNs = System.nanoTime();

    ud.lastUpdateTimeNs = finishUpdateNs;
    ud.lastUpdateEvent = event;

    final float smoothAlpha = isSlow ? 0.8f : 0.3f;
    final float smoothCoAlpha = 1 - smoothAlpha;
    final long spentMs = (finishUpdateNs - startTimeNs) / 1000000L;

    ud.averageUpdateDurationMs = Math.round(spentMs*smoothAlpha + ud.averageUpdateDurationMs*smoothCoAlpha);
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
    if (LightEdit.owns(e.getProject()) && !LightEdit.isActionCompatible(action)) {
      presentation.setEnabledAndVisible(false);
      return false;
    }

    final Boolean wasEnabledBefore = (Boolean)presentation.getClientProperty(WAS_ENABLED_BEFORE_DUMB);
    final boolean dumbMode = isDumbMode(e.getProject());
    if (wasEnabledBefore != null && !dumbMode) {
      presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, null);
      presentation.setEnabled(wasEnabledBefore.booleanValue());
      presentation.setVisible(true);
    }
    final boolean enabledBeforeUpdate = presentation.isEnabled();

    boolean allowed = (!dumbMode || action.isDumbAware()) &&
                      (!Registry.is("actionSystem.honor.modal.context") || !isInModalContext || action.isEnabledInModalContext());

    String presentationText = presentation.getText();
    boolean edt = ApplicationManager.getApplication().isDispatchThread();
    if (edt && insidePerformDumbAwareUpdate++ == 0) {
      ActionPauses.STAT.started();
    }

    action.applyTextOverride(e);

    try {
      if (beforeActionPerformed) {
        action.beforeActionPerformedUpdate(e);
      }
      else {
        action.update(e);
      }
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, !allowed && presentation.isEnabled());
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, !allowed && presentation.isVisible());
    }
    catch (IndexNotReadyException e1) {
      if (!allowed) {
        return true;
      }
      throw e1;
    }
    finally {
      if (edt && --insidePerformDumbAwareUpdate == 0) {
        ActionPauses.STAT.finished(presentationText + " action update (" + action.getClass() + ")");
      }
      if (!allowed) {
        if (wasEnabledBefore == null) {
          presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, enabledBeforeUpdate);
        }
        presentation.setEnabled(false);
      }
    }

    return false;
  }

  /**
   * @deprecated use {@link #performDumbAwareUpdate(boolean, AnAction, AnActionEvent, boolean)} instead
   */
  @Deprecated
  public static boolean performDumbAwareUpdate(@NotNull AnAction action, @NotNull AnActionEvent e, boolean beforeActionPerformed) {
    return performDumbAwareUpdate(false, action, e, beforeActionPerformed);
  }

  /**
   * Show a cancellable modal progress running the given computation under read action with the same {@link DumbService#isAlternativeResolveEnabled()}
   * as the caller. To be used in actions which need to perform potentially long-running computations synchronously without freezing UI.
   * @throws ProcessCanceledException if the user has canceled the progress. If the action can be safely stopped at this point
   *   without leaving inconsistent data behind, this exception doesn't need to be caught and processed.
   */
  public static <T> T underModalProgress(@NotNull Project project,
                                         @NotNull @Nls(capitalization = Nls.Capitalization.Title) String progressTitle,
                                         @NotNull Computable<T> computable) throws ProcessCanceledException {
    DumbService dumbService = DumbService.getInstance(project);
    boolean useAlternativeResolve = dumbService.isAlternativeResolveEnabled();
    ThrowableComputable<T, RuntimeException> inReadAction = () -> ApplicationManager.getApplication().runReadAction(computable);
    ThrowableComputable<T, RuntimeException> prioritizedRunnable = () -> ProgressManager.getInstance().computePrioritized(inReadAction);
    ThrowableComputable<T, RuntimeException> process = useAlternativeResolve ? () -> dumbService.computeWithAlternativeResolveEnabled(prioritizedRunnable) : prioritizedRunnable;
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project);
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
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      if (DumbService.getInstance(openProject).isDumb()) {
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
    return !visibilityMatters || e.getPresentation().isVisible();
  }

  public static void performActionDumbAwareWithCallbacks(@NotNull AnAction action, @NotNull AnActionEvent e, @NotNull DataContext context) {
    final ActionManagerEx manager = ActionManagerEx.getInstanceEx();
    manager.fireBeforeActionPerformed(action, context, e);
    performActionDumbAware(action, e);
    manager.fireAfterActionPerformed(action, context, e);
  }

  public static void performActionDumbAware(AnAction action, AnActionEvent e) {
    try {
      action.actionPerformed(e);
    }
    catch (IndexNotReadyException ex) {
      LOG.info(ex);
      showDumbModeWarning(e);
    }
  }
  @NotNull
  public static AnActionEvent createEmptyEvent() {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataId -> null);
  }

  public static void sortAlphabetically(@NotNull List<? extends AnAction> list) {
    list.sort((o1, o2) -> Comparing.compare(o1.getTemplateText(), o2.getTemplateText()));
  }

  /**
   * Tries to find an 'action' and 'target action' by text and put the 'action' just before of after the 'target action'
   */
  public static void moveActionTo(@NotNull List<AnAction> list,
                                  @NotNull String actionText,
                                  @NotNull String targetActionText,
                                  boolean before) {
    if (Objects.equals(actionText, targetActionText)) {
      return;
    }

    int actionIndex = -1;
    int targetIndex = -1;
    for (int i = 0; i < list.size(); i++) {
      AnAction action = list.get(i);
      if (actionIndex == -1 && Objects.equals(actionText, action.getTemplateText())) actionIndex = i;
      if (targetIndex == -1 && Objects.equals(targetActionText, action.getTemplateText())) targetIndex = i;
      if (actionIndex != -1 && targetIndex != -1) {
        if (actionIndex < targetIndex) targetIndex--;
        AnAction anAction = list.remove(actionIndex);
        list.add(before ? Math.max(0, targetIndex) : targetIndex + 1, anAction);
        return;
      }
    }
  }

  @NotNull
  public static List<AnAction> getActions(@NotNull JComponent component) {
    return ContainerUtil.notNullize(ComponentUtil.getClientProperty(component, AnAction.ACTIONS_KEY));
  }

  public static void clearActions(@NotNull JComponent component) {
    ComponentUtil.putClientProperty(component, AnAction.ACTIONS_KEY, null);
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
        recursiveRegisterShortcutSet((ActionGroup)action, component, parentDisposable);
      }
      action.registerCustomShortcutSet(component, parentDisposable);
    }
  }

  public static boolean recursiveContainsAction(@NotNull ActionGroup group, @NotNull AnAction action) {
    return anyActionFromGroupMatches(group, true, Predicate.isEqual(action));
  }

  public static boolean anyActionFromGroupMatches(@NotNull ActionGroup group, boolean processPopupSubGroups,
                                                  @NotNull Predicate<? super AnAction> condition) {
    for (AnAction child : group.getChildren(null)) {
      if (condition.test(child)) return true;
      if (child instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)child;
        if ((processPopupSubGroups || !childGroup.isPopup()) && anyActionFromGroupMatches(childGroup, processPopupSubGroups, condition)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Convenience method for copying properties from a registered action
   *
   * @param actionId action id
   */
  public static AnAction copyFrom(@NotNull AnAction action, @NotNull @NonNls String actionId) {
    AnAction from = ActionManager.getInstance().getAction(actionId);
    if (from != null) {
      action.copyFrom(from);
    }
    ActionsCollector.getInstance().onActionConfiguredByActionId(action, actionId);
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
      p1.setTextWithMnemonic(p2.getTextWithPossibleMnemonic());
    }
    p1.setDescription(ObjectUtils.chooseNotNull(p1.getDescription(), p2.getDescription()));
    ShortcutSet ss1 = a1.getShortcutSet();
    if (ss1 == CustomShortcutSet.EMPTY) {
      a1.copyShortcutFrom(a2);
    }
    ActionsCollector.getInstance().onActionConfiguredByActionId(action, actionId);
    return a1;
  }

  public static void invokeAction(@NotNull AnAction action,
                                  @NotNull Component component,
                                  @NotNull String place,
                                  @Nullable InputEvent inputEvent,
                                  @Nullable Runnable onDone) {
    invokeAction(action, DataManager.getInstance().getDataContext(component), place, inputEvent, onDone);
  }

  public static void invokeAction(@NotNull AnAction action,
                                  @NotNull DataContext dataContext,
                                  @NotNull String place,
                                  @Nullable InputEvent inputEvent,
                                  @Nullable Runnable onDone) {
    Presentation presentation = action.getTemplatePresentation().clone();
    AnActionEvent event = new AnActionEvent(
      inputEvent, dataContext, place, presentation, ActionManager.getInstance(), 0);
    performDumbAwareUpdate(false, action, event, true);
    final ActionManagerEx manager = ActionManagerEx.getInstanceEx();
    if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
      manager.fireBeforeActionPerformed(action, dataContext, event);
      performActionDumbAware(action, event);
      if (onDone != null) {
        onDone.run();
      }
      manager.fireAfterActionPerformed(action, dataContext, event);
    }
  }

  @NotNull
  public static ActionListener createActionListener(@NotNull String actionId, @NotNull Component component, @NotNull String place) {
    return e -> {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      if (action == null) {
        LOG.warn("Can not find action by id " + actionId);
        return;
      }
      invokeAction(action, component, place, null, null);
    };
  }

  @Nullable
  public static ShortcutSet getMnemonicAsShortcut(@NotNull AnAction action) {
    int mnemonic = KeyEvent.getExtendedKeyCodeForChar(action.getTemplatePresentation().getMnemonic());
    if (mnemonic != KeyEvent.VK_UNDEFINED) {
      KeyboardShortcut ctrlAltShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(mnemonic, ALT_DOWN_MASK | CTRL_DOWN_MASK), null);
      KeyboardShortcut altShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(mnemonic, ALT_DOWN_MASK), null);
      CustomShortcutSet shortcutSet;
      if (SystemInfo.isMac) {
        if (Registry.is("ide.mac.alt.mnemonic.without.ctrl")) {
          shortcutSet = new CustomShortcutSet(ctrlAltShortcut, altShortcut);
        } else {
          shortcutSet = new CustomShortcutSet(ctrlAltShortcut);
        }
      } else {
        shortcutSet = new CustomShortcutSet(altShortcut);
      }
      return shortcutSet;
    }
    return null;
  }

  private static class ActionUpdateData {
    AnActionEvent lastUpdateEvent;
    long lastUpdateTimeNs = 0;
    long averageUpdateDurationMs = 0;
  }
}
