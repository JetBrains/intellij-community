// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.concurrency.ThreadContext.currentThreadContext;
import static com.intellij.concurrency.ThreadContext.installThreadContext;

public final class ActionUtil {
  private static final Logger LOG = Logger.getInstance(ActionUtil.class);

  public static final Key<Boolean> ALLOW_PlAIN_LETTER_SHORTCUTS = Key.create("ALLOW_PlAIN_LETTER_SHORTCUTS");
  @ApiStatus.Internal
  public static final Key<Boolean> ALLOW_ACTION_PERFORM_WHEN_HIDDEN = Key.create("ALLOW_ACTION_PERFORM_WHEN_HIDDEN");

  private static final Key<Boolean> WAS_ENABLED_BEFORE_DUMB = Key.create("WAS_ENABLED_BEFORE_DUMB");
  @ApiStatus.Internal
  public static final Key<Boolean> WOULD_BE_ENABLED_IF_NOT_DUMB_MODE = Key.create("WOULD_BE_ENABLED_IF_NOT_DUMB_MODE");
  private static final Key<Boolean> WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE = Key.create("WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE");

  /** @noinspection removal*/
  public static final Key<@Nls String> SECONDARY_TEXT = Presentation.PROP_VALUE;
  public static final Key<@NonNls String> SEARCH_TAG = Key.create("SEARCH_TAG");
  public static final Key<List<AnAction>> INLINE_ACTIONS = Key.create("INLINE_ACTIONS");

  private ActionUtil() {
  }

  public static void showDumbModeWarning(@Nullable Project project,
                                         @NotNull AnAction action,
                                         AnActionEvent @NotNull ... events) {
    List<String> actionNames = new ArrayList<>();
    for (AnActionEvent event : events) {
      String s = event.getPresentation().getText();
      if (StringUtil.isNotEmpty(s)) {
        actionNames.add(s);
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Showing dumb mode warning for " + Arrays.asList(events), new Throwable());
    }
    if (project == null) return;
    DumbService.getInstance(project).showDumbModeNotificationForAction(getActionUnavailableMessage(actionNames),
                                                                       ActionManager.getInstance().getId(action));
  }

  private static @NotNull @NlsContexts.PopupContent String getActionUnavailableMessage(@NotNull List<String> actionNames) {
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

  public static @NotNull @NlsContexts.PopupContent String getUnavailableMessage(@NotNull String action, boolean plural) {
    if (plural) {
      return IdeBundle.message("popup.content.actions.not.available.while.updating.indices", action,
                               ApplicationNamesInfo.getInstance().getProductName());
    }
    return IdeBundle.message("popup.content.action.not.available.while.updating.indices", action,
                             ApplicationNamesInfo.getInstance().getProductName());
  }

  /**
   * @deprecated Use {@link #performDumbAwareUpdate(AnAction, AnActionEvent, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public static boolean performDumbAwareUpdate(boolean isInModalContext,
                                               @NotNull AnAction action,
                                               @NotNull AnActionEvent e,
                                               boolean beforeActionPerformed) {
    return performDumbAwareUpdate(action, e, beforeActionPerformed);
  }

  /**
   * Calls {@link AnAction#update(AnActionEvent)} or {@link AnAction#beforeActionPerformedUpdate(AnActionEvent)}
   * depending on {@code beforeActionPerformed} value with all the required extra logic around it.
   *
   * @return true if update tried to access indices in dumb mode
   */
  public static boolean performDumbAwareUpdate(@NotNull AnAction action, @NotNull AnActionEvent e, boolean beforeActionPerformed) {
    Presentation presentation = e.getPresentation();
    if (LightEdit.owns(e.getProject()) && !isActionLightEditCompatible(action)) {
      presentation.setEnabledAndVisible(false);
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, false);
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, false);
      return false;
    }

    Boolean wasEnabledBefore = presentation.getClientProperty(WAS_ENABLED_BEFORE_DUMB);
    boolean dumbMode = isDumbMode(e.getProject());
    if (wasEnabledBefore != null && !dumbMode) {
      presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, null);
      presentation.setEnabled(wasEnabledBefore.booleanValue());
      presentation.setVisible(true);
    }
    boolean enabledBeforeUpdate = presentation.isEnabled();
    boolean allowed = !dumbMode || action.isDumbAware();

    action.applyTextOverride(e);
    try {
      if (beforeActionPerformed && e.getUpdateSession() == UpdateSession.EMPTY) {
        IdeUiService.getInstance().initUpdateSession(e);
      }
      Runnable runnable = () -> {
        // init group flags from deprecated methods
        e.setInjectedContext(action.isInInjectedContext());
        if (beforeActionPerformed) {
          action.beforeActionPerformedUpdate(e);
        }
        else {
          action.update(e);
        }
        if (!e.getPresentation().isEnabled() && e.isInInjectedContext()) {
          e.setInjectedContext(false);
          if (beforeActionPerformed) {
            action.beforeActionPerformedUpdate(e);
          }
          else {
            action.update(e);
          }
        }
      };
      boolean isLikeUpdate = !beforeActionPerformed;
      try (AccessToken ignore = SlowOperations.startSection(isLikeUpdate ? SlowOperations.ACTION_UPDATE
                                                                         : SlowOperations.ACTION_PERFORM)) {
        long startTime = System.nanoTime();
        runnable.run();
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        ActionsCollector.getInstance().recordUpdate(action, e, duration);
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
      if (!allowed) {
        if (wasEnabledBefore == null) {
          presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, enabledBeforeUpdate);
        }
        presentation.setEnabled(false);
      }
    }

    return false;
  }

  private static boolean isActionLightEditCompatible(@NotNull AnAction action) {
    if (action instanceof AnActionWrapper wrapper) return isActionLightEditCompatible(wrapper.getDelegate());
    return (action instanceof ActionGroup) && action.isDumbAware() || action instanceof LightEditCompatible;
  }

  /**
   * Show a cancellable modal progress running the given computation under read action with the same {@link DumbService#isAlternativeResolveEnabled()}
   * as the caller. To be used in actions which need to perform potentially long-running computations synchronously without freezing UI.
   *
   * @throws ProcessCanceledException if the user has canceled the progress. If the action can be safely stopped at this point
   *                                  without leaving inconsistent data behind, this exception doesn't need to be caught and processed.
   */
  public static <T> T underModalProgress(@NotNull Project project,
                                         @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                         @NotNull Computable<T> computable) throws ProcessCanceledException {
    DumbService dumbService = DumbService.getInstance(project);
    boolean useAlternativeResolve = dumbService.isAlternativeResolveEnabled();
    ThrowableComputable<T, RuntimeException> inReadAction = () -> ApplicationManager.getApplication().runReadAction(computable);
    ThrowableComputable<T, RuntimeException> prioritizedRunnable = () -> ProgressManager.getInstance().computePrioritized(inReadAction);
    ThrowableComputable<T, RuntimeException> process = useAlternativeResolve
                                                       ? () -> dumbService.computeWithAlternativeResolveEnabled(prioritizedRunnable)
                                                       : prioritizedRunnable;
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project);
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

  public static boolean lastUpdateAndCheckDumb(@NotNull AnAction action, @NotNull AnActionEvent e, boolean visibilityMatters) {
    Project project = e.getProject();
    if (project != null && PerformWithDocumentsCommitted.isPerformWithDocumentsCommitted(action)) {
      try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }
    }
    performDumbAwareUpdate(action, e, true);

    if (project != null && DumbService.getInstance(project).isDumb() && !action.isDumbAware()) {
      if (Boolean.FALSE.equals(e.getPresentation().getClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE))) {
        return false;
      }
      if (visibilityMatters && Boolean.FALSE.equals(e.getPresentation().getClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE))) {
        return false;
      }

      showDumbModeWarning(project, action, e);
      return false;
    }

    if (!e.getPresentation().isEnabled()) {
      return false;
    }
    return !visibilityMatters || e.getPresentation().isVisible();
  }

  /**
   * @deprecated use {@link #performActionDumbAwareWithCallbacks(AnAction, AnActionEvent)}
   */
  @Deprecated(forRemoval = true)
  public static void performActionDumbAwareWithCallbacks(@NotNull AnAction action, @NotNull AnActionEvent e, @NotNull DataContext context) {
    LOG.assertTrue(e.getDataContext() == context, "event context does not match the argument");
    performActionDumbAwareWithCallbacks(action, e);
  }

  public static void performActionDumbAwareWithCallbacks(@NotNull AnAction action, @NotNull AnActionEvent e) {
    performDumbAwareWithCallbacks(action, e, () -> doPerformActionOrShowPopup(action, e, null));
  }

  @ApiStatus.Internal
  public static void doPerformActionOrShowPopup(@NotNull AnAction action,
                                                @NotNull AnActionEvent e,
                                                @Nullable Consumer<? super JBPopup> popupShow) {
    if (action instanceof ActionGroup group && !e.getPresentation().isPerformGroup()) {
      DataContext dataContext = e.getDataContext();
      String place = ActionPlaces.getActionGroupPopupPlace(e.getPlace());
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
        e.getPresentation().getText(), group, dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false, null, -1, null, place);
      var toolbarPopupLocation = CommonActionsPanel.getPreferredPopupPoint(action, dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
      if (toolbarPopupLocation != null) {
        popup.show(toolbarPopupLocation);
      }
      else if (popupShow != null) {
        popupShow.accept(popup);
      }
      else {
        popup.showInBestPositionFor(dataContext);
      }
    }
    else {
      action.actionPerformed(e);
    }
  }

  public static void performInputEventHandlerWithCallbacks(@NotNull InputEvent inputEvent, @NotNull Runnable runnable) {
    String place = inputEvent instanceof KeyEvent ? ActionPlaces.KEYBOARD_SHORTCUT :
                   inputEvent instanceof MouseEvent ? ActionPlaces.MOUSE_SHORTCUT :
                   ActionPlaces.UNKNOWN;
    AnActionEvent event = AnActionEvent.createFromInputEvent(
      inputEvent, place, InputEventDummyAction.INSTANCE.getTemplatePresentation().clone(),
      DataManager.getInstance().getDataContext(Objects.requireNonNull(inputEvent.getComponent())));
    performDumbAwareWithCallbacks(InputEventDummyAction.INSTANCE, event, runnable);
  }

  public static void performDumbAwareWithCallbacks(@NotNull AnAction action,
                                                   @NotNull AnActionEvent event,
                                                   @NotNull Runnable performRunnable) {
    Project project = event.getProject();
    IndexNotReadyException indexError = null;
    ActionManagerEx manager = ActionManagerEx.getInstanceEx();
    manager.fireBeforeActionPerformed(action, event);
    Component component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    String actionId = StringUtil.notNullize(
      event.getActionManager().getId(action),
      action == InputEventDummyAction.INSTANCE ? performRunnable.getClass().getName() :
      action.getClass().getName());
    if (component != null && !UIUtil.isShowing(component) &&
        !ActionPlaces.TOUCHBAR_GENERAL.equals(event.getPlace()) &&
        !Boolean.TRUE.equals(ClientProperty.get(component, ALLOW_ACTION_PERFORM_WHEN_HIDDEN))) {
      LOG.warn("Action is not performed because target component is not showing: " +
               "action=" + actionId + ", component=" + component.getClass().getName());
      manager.fireAfterActionPerformed(action, event, AnActionResult.IGNORED);
      return;
    }
    AnActionResult result = null;
    try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM);
         AccessToken ignore2 = withActionThreadContext(actionId, event.getPlace(), event.getInputEvent(), component)) {
      performRunnable.run();
      result = AnActionResult.PERFORMED;
    }
    catch (IndexNotReadyException ex) {
      indexError = ex;
      result = AnActionResult.failed(ex);
    }
    catch (RuntimeException | Error ex) {
      result = AnActionResult.failed(ex);
      throw ex;
    }
    finally {
      if (result == null) result = AnActionResult.failed(new Throwable());
      manager.fireAfterActionPerformed(action, event, result);
    }
    if (indexError != null) {
      LOG.info(indexError);
      showDumbModeWarning(project, action, event);
    }
  }

  /**
   * @deprecated use {@link #performActionDumbAwareWithCallbacks(AnAction, AnActionEvent)} or
   * {@link AnAction#actionPerformed(AnActionEvent)} instead
   */
  @Deprecated(forRemoval = true)
  public static void performActionDumbAware(@NotNull AnAction action, @NotNull AnActionEvent event) {
    Project project = event.getProject();
    try {
      doPerformActionOrShowPopup(action, event, null);
    }
    catch (IndexNotReadyException ex) {
      LOG.info(ex);
      showDumbModeWarning(project, action, event);
    }
  }

  public static @NotNull AnActionEvent createEmptyEvent() {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, DataContext.EMPTY_CONTEXT);
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
        list.add(before ? targetIndex : targetIndex + 1, anAction);
        return;
      }
    }
  }

  public static @NotNull List<AnAction> getActions(@NotNull JComponent component) {
    List<AnAction> list = ClientProperty.get(component, AnAction.ACTIONS_KEY);
    return list == null ? List.of() : list;
  }

  public static void clearActions(@NotNull JComponent component) {
    ClientProperty.put(component, AnAction.ACTIONS_KEY, null);
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
      if (shortcut instanceof KeyboardShortcut ks) {
        KeyStroke first = ks.getFirstKeyStroke();
        KeyStroke second = ks.getSecondKeyStroke();
        if (second == null) {
          component.registerKeyboardAction(action, first, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
      }
    }
  }

  public static boolean recursiveContainsAction(@NotNull ActionGroup group, @NotNull AnAction action) {
    return anyActionFromGroupMatches(group, true, Predicate.isEqual(action));
  }

  public static boolean anyActionFromGroupMatches(@NotNull ActionGroup group, boolean processPopupSubGroups,
                                                  @NotNull Predicate<? super AnAction> condition) {
    for (AnAction child : group.getChildren(null)) {
      if (condition.test(child)) return true;
      if (child instanceof ActionGroup childGroup) {
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
   * Convenience method for merging non-null properties from a registered action
   *
   * @param action   action to merge to
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
    AnActionEvent event = AnActionEvent.createFromInputEvent(inputEvent, place, presentation, dataContext);
    event.setInjectedContext(action.isInInjectedContext());
    if (lastUpdateAndCheckDumb(action, event, false)) {
      try {
        performActionDumbAwareWithCallbacks(action, event);
      }
      finally {
        if (onDone != null) {
          onDone.run();
        }
      }
    }
  }

  public static @NotNull ActionListener createActionListener(@NotNull String actionId,
                                                             @NotNull Component component,
                                                             @NotNull String place) {
    return e -> {
      AnAction action = getAction(actionId);
      if (action == null) {
        return;
      }
      invokeAction(action, component, place, null, null);
    };
  }

  /**
   * ActionManager.getInstance().getAction(id).registerCustomShortcutSet(shortcutSet, component) must not be used,
   * because it erases shortcuts assigned to this action in keymap.
   * <p>
   * see {@link #wrap(AnAction)}
   */
  public static @NotNull AnAction wrap(@NotNull String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) throw new IllegalArgumentException("No action found with id='" + actionId + "'");
    return action instanceof ActionGroup ? new ActionGroupWrapper((ActionGroup)action) :
           new AnActionWrapper(action);
  }

  /**
   * Wrapping allows altering template presentation and shortcut set without affecting the original action.
   */
  public static @NotNull AnAction wrap(@NotNull AnAction action) {
    return action instanceof ActionGroup ? new ActionGroupWrapper((ActionGroup)action) :
           new AnActionWrapper(action);
  }

  public static @Nullable ShortcutSet getMnemonicAsShortcut(@NotNull AnAction action) {
    return KeymapUtil.getShortcutsForMnemonicCode(action.getTemplatePresentation().getMnemonic());
  }

  @ApiStatus.Experimental
  public static @NotNull ShortcutSet getShortcutSet(@NotNull @NonNls String id) {
    AnAction action = getAction(id);
    return action == null ? CustomShortcutSet.EMPTY : action.getShortcutSet();
  }

  @ApiStatus.Experimental
  public static @Nullable AnAction getAction(@NotNull @NonNls String id) {
    AnAction action = ActionManager.getInstance().getAction(id);
    if (action == null) LOG.warn("Can not find action by id " + id);
    return action;
  }

  @ApiStatus.Experimental
  public static @Nullable ActionGroup getActionGroup(@NotNull @NonNls String id) {
    AnAction action = getAction(id);
    if (action instanceof ActionGroup) return (ActionGroup)action;
    return action == null ? null : new DefaultActionGroup(Collections.singletonList(action));
  }

  @ApiStatus.Experimental
  public static @Nullable ActionGroup getActionGroup(@NonNls String @NotNull ... ids) {
    if (ids.length == 1) return getActionGroup(ids[0]);
    List<AnAction> actions = ContainerUtil.mapNotNull(ids, ActionUtil::getAction);
    return actions.isEmpty() ? null : new DefaultActionGroup(actions);
  }

  public static @NotNull Object getDelegateChainRoot(@NotNull AnAction action) {
    Object delegate = action;
    while (delegate instanceof ActionWithDelegate<?>) {
      delegate = ((ActionWithDelegate<?>)delegate).getDelegate();
    }
    return delegate;
  }

  public static @NotNull AnAction getDelegateChainRootAction(@NotNull AnAction action) {
    while (action instanceof ActionWithDelegate<?>) {
      Object delegate = ((ActionWithDelegate<?>)action).getDelegate();
      if (delegate instanceof AnAction) {
        action = (AnAction)delegate;
      }
      else {
        return action;
      }
    }
    return action;
  }

  @ApiStatus.Experimental
  public static @NotNull JComponent createToolbarComponent(@NotNull JComponent target,
                                                           @NotNull @NonNls String place,
                                                           @NotNull ActionGroup group,
                                                           boolean horizontal) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(place, group, horizontal);
    toolbar.setTargetComponent(target);
    return toolbar.getComponent();
  }

  public static @NotNull AnAction createActionFromSwingAction(@NotNull Action action) {
    AnAction anAction = new AnAction((String)action.getValue(Action.NAME)) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(action.isEnabled());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
      }
    };

    Object value = action.getValue(Action.ACCELERATOR_KEY);
    if (value instanceof KeyStroke keys) {
      anAction.setShortcutSet(new CustomShortcutSet(keys));
    }

    return anAction;
  }

  @ApiStatus.Internal
  @RequiresBlockingContext
  public static @Nullable ActionContextElement getActionThreadContext() {
    return currentThreadContext().get(ActionContextElement.Companion);
  }

  private static final Key<ActionContextElement> ACTION_CONTEXT_ELEMENT_KEY = Key.create("ACTION_CONTEXT_ELEMENT_KEY");

  @ApiStatus.Internal
  public static void initActionContextForComponent(@NotNull JComponent component) {
    ClientProperty.put(component, ACTION_CONTEXT_ELEMENT_KEY, getActionThreadContext());
  }

  private static @NotNull AccessToken withActionThreadContext(@NotNull String actionId,
                                                              @NotNull String place,
                                                              @Nullable InputEvent event,
                                                              @Nullable Component component) {
    ActionContextElement parent = UIUtil.uiParents(component, false)
      .filterMap(o -> ClientProperty.get(o, ACTION_CONTEXT_ELEMENT_KEY)).first();
    return installThreadContext(currentThreadContext().plus(
      new ActionContextElement(actionId, place, event == null ? -1 : event.getID(), parent)), true);
  }

  private static class InputEventDummyAction extends DumbAwareAction implements LightEditCompatible {
    static final InputEventDummyAction INSTANCE = new InputEventDummyAction();
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) { }
  }
}