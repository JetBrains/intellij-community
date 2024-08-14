// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.concurrency.currentThreadContext
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ActionsCollector
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.*
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.CommonActionsPanel
import com.intellij.util.ObjectUtils
import com.intellij.util.SlowOperationCanceledException
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.event.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke

private val LOG = logger<ActionUtil>()
private val InputEventDummyAction = EmptyAction.createEmptyAction(null, null, true)

object ActionUtil {

  @JvmField
  val SHOW_TEXT_IN_TOOLBAR: Key<Boolean> = Key.create("SHOW_TEXT_IN_TOOLBAR")

  @JvmField
  val USE_SMALL_FONT_IN_TOOLBAR: Key<Boolean> = Key.create("USE_SMALL_FONT_IN_TOOLBAR")

  @JvmField
  val TOOLTIP_TEXT: Key<@NlsContexts.Tooltip String> = Key.create(JComponent.TOOL_TIP_TEXT_KEY)

  /**
   * By default, a "performable" non-empty popup action group menu item still shows a submenu.
   * Use this key to disable the submenu and avoid children expansion on update as follows:
   *
   * `presentation.putClientProperty(ActionMenu.SUPPRESS_SUBMENU, true)`.
   *
   * Both ordinary and template presentations are supported.
   * @see Presentation.setPerformGroup
   */
  @JvmField
  val SUPPRESS_SUBMENU: Key<Boolean> = Key.create("SUPPRESS_SUBMENU")

  /**
   * By default, a toolbar button for a popup action group paints the additional "drop-down-arrow" mark over its icon.
   * Use this key to disable the painting of that mark as follows:
   *
   * `presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)`
   *
   * Both ordinary and template presentations are supported.
   * @see Presentation.setPerformGroup
   */
  @JvmField
  val HIDE_DROPDOWN_ICON: Key<Boolean> = Key.create("HIDE_DROPDOWN_ICON");

  @JvmField
  val KEYBOARD_SHORTCUT_SUFFIX: Key<@NlsSafe String> = Key.create("KEYBOARD_SHORTCUT_SUFFIX");

  /** The icon that will be placed after the text */
  @JvmField
  val SECONDARY_ICON: Key<Icon> = Key.create("SECONDARY_ICON")

  /** Same as [CompactActionGroup] */
  @JvmField
  val HIDE_DISABLED_CHILDREN: Key<Boolean> = Key.create("HIDE_DISABLED_CHILDREN")

  /** Same as [AlwaysVisibleActionGroup] */
  @JvmField
  val ALWAYS_VISIBLE_GROUP: Key<Boolean> = Key.create("ALWAYS_VISIBLE_GROUP")

  @JvmField
  val ALWAYS_VISIBLE_INLINE_ACTION: Key<Boolean> = Key.create("ALWAYS_VISIBLE_INLINE_ACTION")

  @JvmField
  val ALLOW_PlAIN_LETTER_SHORTCUTS: Key<Boolean> = Key.create("ALLOW_PlAIN_LETTER_SHORTCUTS")

  @ApiStatus.Internal
  @JvmField
  val ALLOW_ACTION_PERFORM_WHEN_HIDDEN: Key<Boolean> = Key.create("ALLOW_ACTION_PERFORM_WHEN_HIDDEN")

  @JvmField
  @Suppress("DEPRECATION", "removal")
  val SECONDARY_TEXT: Key<@Nls String> = Presentation.PROP_VALUE

  @JvmField
  val SEARCH_TAG: Key<@NonNls String> = Key.create("SEARCH_TAG")

  @JvmField
  val INLINE_ACTIONS: Key<List<AnAction>> = Key.create("INLINE_ACTIONS")

  // Internal keys

  @JvmStatic
  private val WAS_ENABLED_BEFORE_DUMB: Key<Boolean> = Key.create("WAS_ENABLED_BEFORE_DUMB")

  @ApiStatus.Internal
  @JvmField
  val WOULD_BE_ENABLED_IF_NOT_DUMB_MODE: Key<Boolean> = Key.create("WOULD_BE_ENABLED_IF_NOT_DUMB_MODE")

  @JvmStatic
  private val WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE: Key<Boolean> = Key.create("WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE")

  @JvmStatic
  fun showDumbModeWarning(project: Project?,
                          action: AnAction,
                          vararg events: AnActionEvent) {
    val actionNames = events.asSequence()
      .map { it.presentation.text }.filter { it.isNotEmpty() }.toList()
    if (LOG.isDebugEnabled) {
      LOG.debug("Showing dumb mode warning for ${events.asList()}", Throwable())
    }
    if (project == null) return
    DumbService.getInstance(project).showDumbModeNotificationForAction(
      getActionUnavailableMessage(actionNames), ActionManager.getInstance().getId(action))
  }

  @JvmStatic
  private fun getActionUnavailableMessage(actionNames: List<@ActionText String>): @NlsContexts.PopupContent String {
    return when {
      actionNames.isEmpty() -> getUnavailableMessage("This action", false)
      actionNames.size == 1 -> getUnavailableMessage("'${actionNames[0]}'", false)
      else -> getUnavailableMessage("None of the following actions", true) +
              ": ${actionNames.joinToString(", ")}"
    }
  }

  @JvmStatic
  fun getUnavailableMessage(action: String, plural: Boolean): @NlsContexts.PopupContent String {
    if (plural) {
      return IdeBundle.message("popup.content.actions.not.available.while.updating.indices", action,
                               ApplicationNamesInfo.getInstance().productName)
    }
    return IdeBundle.message("popup.content.action.not.available.while.updating.indices", action,
                             ApplicationNamesInfo.getInstance().productName)
  }

  /**
   * Calls [AnAction.update] or [AnAction.beforeActionPerformedUpdate]
   * depending on `beforeActionPerformed` value with all the required extra logic around it.
   *
   * @return true if update tried to access indices in dumb mode
   */
  @JvmStatic
  fun performDumbAwareUpdate(action: AnAction, e: AnActionEvent, beforeActionPerformed: Boolean): Boolean {
    val presentation = e.presentation
    if (LightEdit.owns(e.project) && !isActionLightEditCompatible(action)) {
      presentation.isEnabledAndVisible = false
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, false)
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, false)
      return false
    }
    var beforePerformedMode: String? = null // on|fast_only|old_only|off
    if (beforeActionPerformed) {
      beforePerformedMode = Registry.get("actionSystem.update.beforeActionPerformedUpdate").selectedOption
      val updateThread = action.getActionUpdateThread()
      if (beforePerformedMode == "off" || beforePerformedMode == "old_only" &&
          (updateThread == ActionUpdateThread.BGT ||
           updateThread == ActionUpdateThread.EDT)) {
        return false
      }
    }
    val wasEnabledBefore = presentation.getClientProperty(WAS_ENABLED_BEFORE_DUMB)
    val dumbMode = isDumbMode(e.project)
    if (wasEnabledBefore != null && !dumbMode) {
      presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, null)
      presentation.isEnabled = wasEnabledBefore
      presentation.isVisible = true
    }
    val enabledBeforeUpdate = presentation.isEnabled
    val allowed = !dumbMode || action.isDumbAware
    action.applyTextOverride(e)
    try {
      if (beforeActionPerformed && e.updateSession === UpdateSession.EMPTY) {
        IdeUiService.getInstance().initUpdateSession(e)
      }
      val runnable = {
        e.setInjectedContext(action.isInInjectedContext)
        if (beforeActionPerformed) {
          action.beforeActionPerformedUpdate(e)
        }
        else {
          action.update(e)
        }
        if (!e.presentation.isEnabled && e.isInInjectedContext) {
          e.setInjectedContext(false)
          if (beforeActionPerformed) {
            action.beforeActionPerformedUpdate(e)
          }
          else {
            action.update(e)
          }
        }
      }
      val sectionName =
        if (beforeActionPerformed && beforePerformedMode == "fast_only") SlowOperations.FORCE_THROW
        else if (beforeActionPerformed) SlowOperations.ACTION_PERFORM
        else SlowOperations.ACTION_UPDATE
      SlowOperations.startSection(sectionName).use {
        val startTime = System.nanoTime()
        runnable()
        val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
        ActionsCollector.getInstance().recordUpdate(action, e, duration)
      }
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, !allowed && presentation.isEnabled)
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, !allowed && presentation.isVisible)
    }
    catch (_: SlowOperationCanceledException) {
      return false
    }
    catch (ex: IndexNotReadyException) {
      if (!allowed) {
        return true
      }
      throw ex
    }
    finally {
      if (!allowed) {
        if (wasEnabledBefore == null) {
          presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, enabledBeforeUpdate)
        }
        presentation.isEnabled = false
      }
    }
    return false
  }

  @JvmStatic
  private fun isActionLightEditCompatible(action: AnAction): Boolean {
    if (action is AnActionWrapper) return isActionLightEditCompatible(action.delegate)
    return (action is ActionGroup) && action.isDumbAware() || action is LightEditCompatible
  }

  /**
   * Show a cancellable modal progress running the given computation under read action with the same [DumbService.isAlternativeResolveEnabled]
   * as the caller. To be used in actions which need to perform potentially long-running computations synchronously without freezing UI.
   *
   * @throws ProcessCanceledException if the user has canceled the progress. If the action can be safely stopped at this point
   * without leaving inconsistent data behind, this exception doesn't need to be caught and processed.
   */
  @Throws(ProcessCanceledException::class)
  @JvmStatic
  fun <T> underModalProgress(project: Project,
                             progressTitle: @NlsContexts.ProgressTitle String,
                             computable: Computable<T>): T {
    val dumbService = DumbService.getInstance(project)
    val useAlternativeResolve = dumbService.isAlternativeResolveEnabled
    val inReadAction = ThrowableComputable<T, RuntimeException> { ApplicationManager.getApplication().runReadAction(computable) }
    val prioritizedRunnable = ThrowableComputable<T, RuntimeException> { ProgressManager.getInstance().computePrioritized(inReadAction) }
    val process = if (useAlternativeResolve) ThrowableComputable {
      dumbService.computeWithAlternativeResolveEnabled(prioritizedRunnable) }
    else prioritizedRunnable
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project)
  }

  /**
   * @return whether a dumb mode is in progress for the passed project or, if the argument is null, for any open project.
   * @see DumbService
   */
  @JvmStatic
  fun isDumbMode(project: Project?): Boolean {
    if (project != null) {
      return DumbService.getInstance(project).isDumb
    }
    for (openProject in ProjectManager.getInstance().openProjects) {
      if (DumbService.getInstance(openProject).isDumb) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun lastUpdateAndCheckDumb(action: AnAction, e: AnActionEvent, visibilityMatters: Boolean): Boolean {
    val project = e.project
    PerformWithDocumentsCommitted.commitDocumentsIfNeeded(action, e)
    performDumbAwareUpdate(action, e, true)
    if (project != null && DumbService.getInstance(project).isDumb && !action.isDumbAware) {
      if (e.presentation.getClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE) == false) {
        return false
      }
      if (visibilityMatters && e.presentation.getClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE) == false) {
        return false
      }
      showDumbModeWarning(project, action, e)
      return false
    }
    if (!e.presentation.isEnabled) {
      return false
    }
    return !visibilityMatters || e.presentation.isVisible
  }

  @JvmStatic
  fun performActionDumbAwareWithCallbacks(action: AnAction, event: AnActionEvent) {
    (event.actionManager as ActionManagerEx).performWithActionCallbacks(action, event) {
      doPerformActionOrShowPopup(action, event, null)
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun doPerformActionOrShowPopup(action: AnAction,
                                 e: AnActionEvent,
                                 popupShow: Consumer<in JBPopup?>?) {
    if (action is ActionGroup && !e.presentation.isPerformGroup) {
      val dataContext = e.dataContext
      val place = ActionPlaces.getActionGroupPopupPlace(e.place)
      val popup: ListPopup = JBPopupFactory.getInstance().createActionGroupPopup(
        e.presentation.text, action, dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false, null, -1, null, place)
      val toolbarPopupLocation = CommonActionsPanel.getPreferredPopupPoint(
        action, dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
      if (toolbarPopupLocation != null) {
        popup.show(toolbarPopupLocation)
      }
      else if (popupShow != null) {
        popupShow.accept(popup)
      }
      else {
        popup.showInBestPositionFor(dataContext)
      }
    }
    else {
      action.actionPerformed(e)
    }
  }

  @JvmStatic
  fun performInputEventHandlerWithCallbacks(inputEvent: InputEvent, runnable: Runnable) {
    val place = if (inputEvent is KeyEvent) ActionPlaces.KEYBOARD_SHORTCUT else if (inputEvent is MouseEvent) ActionPlaces.MOUSE_SHORTCUT else ActionPlaces.UNKNOWN
    val event = AnActionEvent.createFromInputEvent(
      inputEvent, place, InputEventDummyAction.templatePresentation.clone(),
      DataManager.getInstance().getDataContext(Objects.requireNonNull(inputEvent.component)))
    (event.actionManager as ActionManagerEx).performWithActionCallbacks(InputEventDummyAction, event, runnable)
  }

  @JvmStatic
  fun performDumbAwareWithCallbacks(action: AnAction,
                                    event: AnActionEvent,
                                    performRunnable: Runnable) {
    (event.actionManager as ActionManagerEx).performWithActionCallbacks(action,  event, performRunnable)
  }

  @JvmStatic
  fun createEmptyEvent(): AnActionEvent {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, DataContext.EMPTY_CONTEXT)
  }

  @JvmStatic
  fun getActions(component: JComponent): List<AnAction> {
    return ClientProperty.get(component, AnAction.ACTIONS_KEY) ?: listOf()
  }

  @JvmStatic
  fun clearActions(component: JComponent) {
    ClientProperty.put(component, AnAction.ACTIONS_KEY, null)
  }

  @JvmStatic
  fun copyRegisteredShortcuts(to: JComponent, from: JComponent) {
    for (anAction in getActions(from)) {
      anAction.registerCustomShortcutSet(anAction.shortcutSet, to)
    }
  }

  @JvmStatic
  fun registerForEveryKeyboardShortcut(component: JComponent,
                                       action: ActionListener,
                                       shortcuts: ShortcutSet) {
    for (shortcut in shortcuts.shortcuts) {
      if (shortcut is KeyboardShortcut) {
        val first: KeyStroke = shortcut.firstKeyStroke
        val second: KeyStroke? = shortcut.secondKeyStroke
        if (second == null) {
          component.registerKeyboardAction(action, first, JComponent.WHEN_IN_FOCUSED_WINDOW)
        }
      }
    }
  }

  /**
   * Convenience method for copying properties from a registered action
   *
   * @param actionId action id
   */
  @JvmStatic
  fun copyFrom(action: AnAction, actionId: @NonNls String): AnAction {
    val from = ActionManager.getInstance().getAction(actionId)
    if (from != null) {
      action.copyFrom(from)
    }
    ActionsCollector.getInstance().onActionConfiguredByActionId(action, actionId)
    ActionCopiedShortcutsTracker.getInstance().onActionCopiedFromId(action, actionId)
    return action
  }

  /**
   * Convenience method for merging non-null properties from a registered action
   *
   * @param action   action to merge to
   * @param actionId action id to merge from
   */
  @JvmStatic
  fun mergeFrom(action: AnAction, actionId: String): AnAction {
    val a1 = action
    val a2 = ActionManager.getInstance().getAction(actionId)
    val p1 = a1.templatePresentation
    val p2 = a2.templatePresentation
    p1.icon = ObjectUtils.chooseNotNull(p1.icon, p2.icon)
    p1.disabledIcon = ObjectUtils.chooseNotNull(p1.disabledIcon, p2.disabledIcon)
    p1.selectedIcon = ObjectUtils.chooseNotNull(p1.selectedIcon, p2.selectedIcon)
    p1.hoveredIcon = ObjectUtils.chooseNotNull(p1.hoveredIcon, p2.hoveredIcon)
    if (StringUtil.isEmpty(p1.text)) {
      p1.setTextWithMnemonic(p2.textWithPossibleMnemonic)
    }
    p1.description = ObjectUtils.chooseNotNull(p1.description, p2.description)
    val ss1 = a1.shortcutSet
    if (ss1 === CustomShortcutSet.EMPTY) {
      a1.copyShortcutFrom(a2)
    }
    ActionsCollector.getInstance().onActionConfiguredByActionId(action, actionId)
    return a1
  }

  @JvmStatic
  fun invokeAction(action: AnAction,
                   component: Component,
                   place: String,
                   inputEvent: InputEvent?,
                   onDone: Runnable?) {
    invokeAction(action, DataManager.getInstance().getDataContext(component), place, inputEvent, onDone)
  }

  @JvmStatic
  fun invokeAction(action: AnAction,
                   dataContext: DataContext,
                   place: String,
                   inputEvent: InputEvent?,
                   onDone: Runnable?) {
    val presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createFromInputEvent(inputEvent, place, presentation, dataContext)
    event.setInjectedContext(action.isInInjectedContext)
    if (lastUpdateAndCheckDumb(action, event, false)) {
      try {
        performActionDumbAwareWithCallbacks(action, event)
      }
      finally {
        onDone?.run()
      }
    }
  }

  @JvmStatic
  fun createActionListener(actionId: String,
                           component: Component,
                           place: String): ActionListener {
    return ActionListener { e: ActionEvent? ->
      val action = getAction(actionId) ?: return@ActionListener
      invokeAction(action, component, place, null, null)
    }
  }

  /**
   * ActionManager.getInstance().getAction(id).registerCustomShortcutSet(shortcutSet, component) must not be used,
   * because it erases shortcuts assigned to this action in keymap.
   */
  @JvmStatic
  fun wrap(actionId: String): AnAction {
    val action = ActionManager.getInstance().getAction(actionId)
                 ?: throw IllegalArgumentException("No action found with id='$actionId'")
    return wrap(action)
  }

  /**
   * Wrapping allows altering template presentation and shortcut set without affecting the original action.
   */
  @JvmStatic
  fun wrap(action: AnAction): AnAction {
    return if (action is ActionGroup) ActionGroupWrapper(action)
    else AnActionWrapper(action)
  }

  @JvmStatic
  fun getMnemonicAsShortcut(action: AnAction): ShortcutSet? {
    return KeymapUtil.getShortcutsForMnemonicCode(action.templatePresentation.mnemonic)
  }

  @ApiStatus.Experimental
  @JvmStatic
  fun getShortcutSet(id: @NonNls String): ShortcutSet {
    return getAction(id)?.shortcutSet ?: CustomShortcutSet.EMPTY
  }

  @ApiStatus.Experimental
  @JvmStatic
  fun getAction(id: @NonNls String): AnAction? {
    val action = ActionManager.getInstance().getAction(id)
    if (action == null) LOG.warn("Can not find action by id $id")
    return action
  }

  @ApiStatus.Experimental
  @JvmStatic
  fun getActionGroup(id: @NonNls String): ActionGroup? {
    val action = getAction(id) ?: return null
    return if (action is ActionGroup) action
    else DefaultActionGroup(listOf(action))
  }

  @ApiStatus.Experimental
  @JvmStatic
  fun getActionGroup(vararg ids: String): ActionGroup? {
    if (ids.size == 1) return getActionGroup(ids[0])
    val actions = ids.mapNotNull { getAction(it) }
    return if (actions.isEmpty()) null else DefaultActionGroup(actions)
  }

  @JvmStatic
  fun getDelegateChainRoot(action: AnAction): Any {
    var delegate: Any = action
    while (delegate is ActionWithDelegate<*>) {
      delegate = delegate.delegate
    }
    return delegate
  }

  @JvmStatic
  fun getDelegateChainRootAction(action: AnAction): AnAction {
    var action = action
    while (action is ActionWithDelegate<*>) {
      val delegate = (action as ActionWithDelegate<*>).delegate
      if (delegate is AnAction) {
        action = delegate
      }
      else {
        return action
      }
    }
    return action
  }

  @ApiStatus.Experimental
  @JvmStatic
  fun createToolbarComponent(target: JComponent,
                             place: @NonNls String,
                             group: ActionGroup,
                             horizontal: Boolean): JComponent {
    val toolbar = ActionManager.getInstance().createActionToolbar(place, group, horizontal)
    toolbar.targetComponent = target
    return toolbar.component
  }

  @JvmStatic
  fun createActionFromSwingAction(action: Action): AnAction {
    val anAction: AnAction = object : AnAction(action.getValue(Action.NAME) as String) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = action.isEnabled
      }

      override fun actionPerformed(e: AnActionEvent) {
        action.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, null))
      }
    }
    val value = action.getValue(Action.ACCELERATOR_KEY)
    if (value is KeyStroke) {
      anAction.shortcutSet = CustomShortcutSet(value)
    }
    return anAction
  }

  @RequiresBlockingContext
  @ApiStatus.Internal
  @JvmStatic
  fun getActionThreadContext(): ActionContextElement? = currentThreadContext()[ActionContextElement]

  @ApiStatus.Internal
  @JvmStatic
  fun initActionContextForComponent(component: JComponent) {
    ActionContextElement.reset(component, getActionThreadContext())
  }
}

