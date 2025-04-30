// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.concurrency.currentThreadContext
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ActionsCollector
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.SHOW_TEXT_IN_TOOLBAR
import com.intellij.openapi.actionSystem.ex.ActionUtil.performAction
import com.intellij.openapi.actionSystem.ex.ActionUtil.updateAction
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
import com.intellij.platform.ide.core.permissions.Permission
import com.intellij.platform.ide.core.permissions.PermissionDeniedException
import com.intellij.platform.ide.core.permissions.RequiresPermissions
import com.intellij.platform.ide.core.permissions.checkPermissionsGranted
import com.intellij.ui.ClientProperty
import com.intellij.util.ObjectUtils
import com.intellij.util.SlowOperationCanceledException
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.event.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke

private val LOG = logger<ActionUtil>()
private val InputEventDummyAction = EmptyAction.createEmptyAction(null, null, true)

/**
 * Public Action System utility class.
 *
 * 1. Always use [updateAction] and [performAction] instead of [ApiStatus.OverrideOnly] [AnAction] methods
 * 2. Use presentation key constants like [SHOW_TEXT_IN_TOOLBAR] to further tweak an action presentations
 * 3. Avoid using deprecated methods
 */
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

  /** Hide disabled child actions */
  @JvmField
  val HIDE_DISABLED_CHILDREN: Key<Boolean> = Key.create("HIDE_DISABLED_CHILDREN")

  /** Avoid updating child actions to check if the group is non-empty */
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
  val SECONDARY_TEXT: Key<@Nls String> = Key.create("SECONDARY_TEXT")

  @JvmField
  val SEARCH_TAG: Key<@NonNls String> = Key.create("SEARCH_TAG")

  @JvmField
  val INLINE_ACTIONS: Key<List<AnAction>> = Key.create("INLINE_ACTIONS")

  @JvmField
  val COMPONENT_PROVIDER: Key<CustomComponentAction> = Key.create("COMPONENT_PROVIDER")

  @JvmField
  val POPUP_HANDLER: Key<Consumer<in JBPopup>> = Key.create("POPUP_HANDLER")

  @ApiStatus.Internal
  @JvmField
  val ACTION_GROUP_POPUP_CAPTION: Key<ActionGroupPopupCaption> = Key.create("ACTION_GROUP_POPUP_CAPTION")

  @ApiStatus.Internal
  enum class ActionGroupPopupCaption {
    /** No popup caption */
    NONE,

    /** Use the text of ActionGroup presentation as a popup caption */
    FROM_ACTION_TEXT,
  }

  // Internal keys

  @JvmStatic
  private val WAS_ENABLED_BEFORE_DUMB: Key<Boolean> = Key.create("WAS_ENABLED_BEFORE_DUMB")

  @ApiStatus.Internal
  @JvmField
  val WOULD_BE_ENABLED_IF_NOT_DUMB_MODE: Key<Boolean> = Key.create("WOULD_BE_ENABLED_IF_NOT_DUMB_MODE")

  @ApiStatus.Internal
  @JvmField
  val UNSATISFIED_PERMISSIONS: Key<List<Permission>> = Key.create("UNSATISFIED_PERMISSIONS")

  @JvmStatic
  private val WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE: Key<Boolean> = Key.create("WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE")

  @ApiStatus.Internal
  @JvmStatic
  fun showDumbModeWarning(
    project: Project?,
    action: AnAction,
    vararg events: AnActionEvent,
  ) {
    val actionNames = events.asSequence()
      .mapNotNull { it.presentation.text }.filter { it.isNotEmpty() }.toList()
    if (LOG.isDebugEnabled) {
      LOG.debug("Showing dumb mode warning for ${events.asList()}", Throwable())
    }
    if (project == null) return
    DumbService.getInstance(project).showDumbModeNotificationForAction(
      getActionsUnavailableMessage(actionNames), ActionManager.getInstance().getId(action))
  }

  @ApiStatus.Internal
  @Deprecated("Use getActionUnavailableMessage(@ActionText String?) or getActionsUnavailableMessage(actionNames: List<@ActionText String>)")
  @JvmStatic
  fun getUnavailableMessage(action: String, plural: Boolean): @NlsContexts.PopupContent String {
    if (plural) {
      return IdeBundle.message("popup.content.actions.not.available.while.updating.indices", action,
                               ApplicationNamesInfo.getInstance().productName)
    }
    return IdeBundle.message("popup.content.action.not.available.while.updating.indices", action,
                             ApplicationNamesInfo.getInstance().productName)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getActionUnavailableMessage(@ActionText action: String?): @NlsContexts.PopupContent String {
    val productName = ApplicationNamesInfo.getInstance().productName
    if (action == null) return IdeBundle.message("popup.content.this.action.not.available.while.updating.indices", productName)
    return IdeBundle.message("popup.content.action.not.available.while.updating.indices", action, productName)
  }

  @JvmStatic
  private fun getActionsUnavailableMessage(actionNames: List<@ActionText String>): @NlsContexts.PopupContent String {
    return when {
      actionNames.isEmpty() -> getActionUnavailableMessage(null)
      actionNames.size == 1 -> getActionUnavailableMessage(actionNames[0])
      else -> IdeBundle.message("popup.content.none.of.following.actions.are.available.while.updating.indices",
                                ApplicationNamesInfo.getInstance().productName,
                                actionNames.joinToString(", "))
    }
  }

  /**
   * Calls [AnAction.update] with proper context, checks and notifications.
   * Does nothing if [beforeActionPerformed] is true.]
   *
   * @return true if update tried to access indices in dumb mode
   */
  @Deprecated("Use updateAction(action, event) instead")
  @JvmStatic
  fun performDumbAwareUpdate(action: AnAction, e: AnActionEvent, beforeActionPerformed: Boolean): Boolean {
    if (beforeActionPerformed) {
      return false
    }
    val result = updateAction(action, e)
    return if (result.isFailed) result.failureCause is IndexNotReadyException else false
  }

  /**
   * Calls [AnAction.update] with proper context, checks and notifications.
   */
  @JvmStatic
  fun updateAction(action: AnAction, e: AnActionEvent): AnActionResult {
    val checkDumb = Registry.`is`("actionSystem.update.dumb.mode.check.awareness")
    val presentation = e.presentation
    if (LightEdit.owns(e.project) && !isActionLightEditCompatible(action)) {
      presentation.isEnabledAndVisible = false
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, false)
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, false)
      return AnActionResult.IGNORED
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
    var isPerformed = false
    action.applyTextOverride(e)
    try {
      val runnable = {
        e.setInjectedContext(action.isInInjectedContext)
        action.update(e)
        if (!e.presentation.isEnabled && e.isInInjectedContext) {
          e.setInjectedContext(false)
          action.update(e)
        }
      }
      SlowOperations.startSection(SlowOperations.ACTION_UPDATE).use {
        val startTime = System.nanoTime()
        runnable()
        isPerformed = true
        val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
        ActionsCollector.getInstance().recordUpdate(action, e, duration)
      }
      presentation.putClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE, !allowed && presentation.isEnabled)
      presentation.putClientProperty(WOULD_BE_VISIBLE_IF_NOT_DUMB_MODE, !allowed && presentation.isVisible)
      if (presentation.isEnabled && action is RequiresPermissions) {
        checkPermissionsGranted(*action.getRequiredPermissions().toTypedArray())
      }
    }
    catch (@Suppress("IncorrectCancellationExceptionHandling") ex: SlowOperationCanceledException) {
      return AnActionResult.failed(ex)
    }
    catch (ex: IndexNotReadyException) {
      if (!allowed && wasEnabledBefore == null) {
        presentation.putClientProperty(WAS_ENABLED_BEFORE_DUMB, enabledBeforeUpdate)
      }
      if (!checkDumb || !allowed) {
        return AnActionResult.failed(ex)
      }
      throw ex
    }
    catch (pde: PermissionDeniedException) {
      if (Registry.`is`("ide.permissions.api.enabled")) {
        presentation.putClientProperty(UNSATISFIED_PERMISSIONS, pde.permissions)
      }
      else {
        LOG.error(Throwable("PDE must not be thrown when `ide.permissions.api.enabled=false`", pde))
      }
    }
    finally {
      if (!isPerformed) {
        presentation.isEnabled = false
      }
    }
    return AnActionResult.PERFORMED
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
  fun <T> underModalProgress(
    project: Project,
    progressTitle: @NlsContexts.ProgressTitle String,
    computable: Computable<T>,
  ): T {
    val dumbService = DumbService.getInstance(project)
    val useAlternativeResolve = dumbService.isAlternativeResolveEnabled
    val inReadAction = ThrowableComputable<T, RuntimeException> { ApplicationManager.getApplication().runReadAction(computable) }
    val prioritizedRunnable = ThrowableComputable<T, RuntimeException> { ProgressManager.getInstance().computePrioritized(inReadAction) }
    val process = if (useAlternativeResolve) ThrowableComputable {
      dumbService.computeWithAlternativeResolveEnabled(prioritizedRunnable)
    }
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

  @Deprecated("Not needed. Use [performAction] only")
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

  /**
   * Calls [AnAction.actionPerformed] with proper context, checks and notifications.
   */
  @JvmStatic
  fun performAction(action: AnAction, event: AnActionEvent): AnActionResult {
    val actionManager = event.actionManager as ActionManagerEx
    val result = actionManager.performWithActionCallbacks(action, event) {
      doPerformActionOrShowPopup(action, event, null)
    }
    if (result.isIgnored && event.project.let { it != null && DumbService.getInstance(it).isDumb && !action.isDumbAware }) {
      if (event.presentation.getClientProperty(WOULD_BE_ENABLED_IF_NOT_DUMB_MODE) != false) {
        showDumbModeWarning(event.project, action, event)
      }
    }
    return result
  }

  @Deprecated("Use [performAction] instead")
  @JvmStatic
  fun performActionDumbAwareWithCallbacks(action: AnAction, event: AnActionEvent) {
    performAction(action, event)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun doPerformActionOrShowPopup(
    action: AnAction,
    e: AnActionEvent,
    popupShow: Consumer<in JBPopup>?,
  ) {
    if (action is RequiresPermissions) {
      checkPermissionsGranted(*action.getRequiredPermissions().toTypedArray())
    }
    if (action is ActionGroup && !e.presentation.isPerformGroup) {
      val dataContext = e.dataContext
      val place = ActionPlaces.getActionGroupPopupPlace(e.place)
      val caption = when (e.presentation.getClientProperty(ACTION_GROUP_POPUP_CAPTION)) {
        ActionGroupPopupCaption.NONE -> null
        ActionGroupPopupCaption.FROM_ACTION_TEXT, null -> e.presentation.text
      }
      val popup: ListPopup = JBPopupFactory.getInstance().createActionGroupPopup(
        caption, action, dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false, null, -1, null, place)
      if (popupShow != null) {
        popupShow.accept(popup)
      }
      else {
        popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(action, e))
      }
    }
    else {
      action.actionPerformed(e)
    }
  }

  /** Prefer regular [performAction] */
  @ApiStatus.Internal
  @JvmStatic
  fun performInputEventHandlerWithCallbacks(uiKind: ActionUiKind, place: String?, inputEvent: InputEvent, runnable: Runnable) {
    val place = place ?: when (inputEvent) {
      is KeyEvent -> ActionPlaces.KEYBOARD_SHORTCUT
      is MouseEvent -> ActionPlaces.MOUSE_SHORTCUT
      else -> ActionPlaces.UNKNOWN
    }
    val context = DataManager.getInstance().getDataContext(inputEvent.component)
    val event = AnActionEvent.createEvent(InputEventDummyAction, context, null, place, uiKind, inputEvent)
    val actionManager = event.actionManager as ActionManagerEx
    actionManager.performWithActionCallbacks(InputEventDummyAction, event, runnable)
  }

  /** Prefer regular [performAction] */
  @ApiStatus.Internal
  @Deprecated("Use [performAction] or [ActionManagerEx.performWithActionCallbacks] instead")
  @JvmStatic
  fun performDumbAwareWithCallbacks(
    action: AnAction,
    event: AnActionEvent,
    performRunnable: Runnable,
  ) {
    (event.actionManager as ActionManagerEx).performWithActionCallbacks(action, event, performRunnable)
  }

  @JvmStatic
  fun createEmptyEvent(): AnActionEvent {
    return AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
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
  fun registerForEveryKeyboardShortcut(
    component: JComponent,
    action: ActionListener,
    shortcuts: ShortcutSet,
  ) {
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

  @Deprecated("Use [invokeAction(action, event, onDone)] instead")
  @JvmStatic
  fun invokeAction(
    action: AnAction,
    component: Component,
    place: String,
    inputEvent: InputEvent?,
    onDone: Runnable?,
  ) {
    val uiKind = if (ActionPlaces.isPopupPlace(place)) ActionUiKind.POPUP else ActionUiKind.NONE
    val dataContext = DataManager.getInstance().getDataContext(component)
    val event = AnActionEvent.createEvent(action, dataContext, null, place, uiKind, inputEvent)
    invokeAction(action, event, onDone)
  }

  @Deprecated("Use [performAction(action, event)] instead")
  @JvmStatic
  fun invokeAction(
    action: AnAction,
    dataContext: DataContext,
    place: String,
    inputEvent: InputEvent?,
    onDone: Runnable?,
  ) {
    val uiKind = if (ActionPlaces.isPopupPlace(place)) ActionUiKind.POPUP else ActionUiKind.NONE
    val event = AnActionEvent.createEvent(action, dataContext, null, place, uiKind, inputEvent)
    invokeAction(action, event, onDone)
  }

  @Deprecated("Use [performAction(action, event)] instead")
  @JvmStatic
  fun invokeAction(action: AnAction, event: AnActionEvent, onDone: Runnable?) {
    val result = performAction(action, event)
    if (!result.isIgnored) onDone?.run()
  }

  @JvmStatic
  fun createActionListener(
    actionId: String,
    component: Component,
    place: String,
  ): ActionListener {
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
  fun createToolbarComponent(
    target: JComponent,
    place: @NonNls String,
    group: ActionGroup,
    horizontal: Boolean,
  ): JComponent {
    val toolbar = ActionManager.getInstance().createActionToolbar(place, group, horizontal)
    toolbar.targetComponent = target
    return toolbar.component
  }

  @JvmStatic
  @JvmOverloads
  fun createActionFromSwingAction(action: Action, dumbAware: Boolean = false): AnAction {
    val anAction: AnAction = object : AnAction(action.getValue(Action.NAME) as String) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = action.isEnabled
      }

      override fun actionPerformed(e: AnActionEvent) {
        action.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, null))
      }

      override fun isDumbAware(): Boolean = dumbAware
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

