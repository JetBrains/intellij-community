// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SimplifiableServiceRetrieving", "ReplaceGetOrSet")

package com.intellij.execution.ui

import com.intellij.execution.AdditionalRunningOptions
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorActionStatus
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ExecutorAction
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.actions.StopAction
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.impl.isOfSameType
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUiSizes
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DecorativeElement
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.PopupShowingTimeTracker
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.ELLIPSIS
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.ui.BadgeRectProvider
import com.intellij.ui.ColorUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.TextHoledIcon
import com.intellij.ui.icons.TextIcon
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.util.function.Predicate
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants

internal const val CONFIGURATION_NAME_TRIM_SUFFIX_LENGTH: Int = 8
internal const val CONFIGURATION_NAME_NON_TRIM_MAX_LENGTH: Int = 33 + CONFIGURATION_NAME_TRIM_SUFFIX_LENGTH

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class RunWidgetResumeManager(private val project: Project) {
  companion object {
    fun getInstance(project: Project): RunWidgetResumeManager = project.service()
  }

  fun getDebugDescriptor(configuration: RunnerAndConfigurationSettings): RunContentDescriptor? {
    val executionManager = ExecutionManagerImpl.getInstance(project)
    return executionManager.getRunningDescriptors {
      configuration === it ||
      configuration.configuration === ExecutionManagerImpl.getDelegatedRunProfile(it.configuration)
    }.firstOrNull {
      executionManager.getExecutors(it).firstOrNull { it.id == ToolWindowId.DEBUG } != null
    }
  }
}

private fun createRunActionToolbar(): ActionToolbar {
  val group = object : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      if (e == null) return emptyArray()
      return arrayOf(e.actionManager.getAction("RunToolbarMainActionGroup"))
    }

    override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
      return filterOutRunIfDebugResumeIsPresent(e, visibleChildren)
    }
  }
  val toolbar = ActionToolbarImpl(ActionPlaces.NEW_UI_RUN_TOOLBAR, group, true)
  toolbar.targetComponent = null
  toolbar.isReservePlaceAutoPopupIcon = false
  toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
  toolbar.component.isOpaque = false
  val toolbarInsetsSupplier: Supplier<Insets> = Supplier {
    // If the toolbar has other actions to the left (added by the user), then the inset is needed (IJPL-145970),
    // otherwise it isn't needed because we have a 20px gap between the center and the right toolbar anyway.
    @Suppress("UseDPIAwareInsets") // the supplier must provide unscaled values
    Insets(0, if (isFirst(toolbar)) 0 else 12 , 0, 16)
  }
  toolbar.component.border = JBUI.Borders.empty(JBInsets.create(toolbarInsetsSupplier, toolbarInsetsSupplier.get()))
  toolbar.setMinimumButtonSize {
    JBUI.size(JBUI.CurrentTheme.RunWidget.actionButtonWidth(), JBUI.CurrentTheme.RunWidget.toolbarHeight())
  }
  val buttonInsetsSupplier: Supplier<Insets> = Supplier {
    val horizontal = JBUI.CurrentTheme.RunWidget.toolbarBorderDirectionalGap()
    val mainToolbarInsets = (JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets() as JBInsets).unscaled
    @Suppress("UseDPIAwareInsets") // the supplier must provide unscaled values
    Insets(mainToolbarInsets.top, horizontal, mainToolbarInsets.bottom, horizontal)
  }
  toolbar.setActionButtonBorder(JBEmptyBorder(JBInsets.create(buttonInsetsSupplier, buttonInsetsSupplier.get())))
  toolbar.setCustomButtonLook(RunWidgetButtonLook())
  return toolbar
}

private fun isFirst(toolbar: ActionToolbarImpl): Boolean {
  val component = toolbar.component
  val parent = component.parent ?: return true // doesn't really matter, as it's not showing
  return parent.getComponent(0) == component
}

private val runToolbarDataKey = Key.create<Boolean>("run-toolbar-data")

internal class RedesignedRunToolbarWrapper : WindowHeaderPlaceholder() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent): Unit = error("Should not be invoked")

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return createRunActionToolbar().component
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.putClientProperty(runToolbarDataKey, isSomeRunningNow(e))
  }

  private fun isSomeRunningNow(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val selectedConfiguration: RunnerAndConfigurationSettings? = RunManager.getInstanceIfCreated(project)?.selectedConfiguration

    (selectedConfiguration?.configuration as? CompoundRunConfiguration)?.let {
      return it.hasRunningSingletons(null)
    }

    if (selectedConfiguration == null) {
      if (!RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
        return false
      }
      val runningDescriptors = ExecutionManagerImpl.getInstance(project)
        .getRunningDescriptors { (it as? RunnerAndConfigurationSettingsImpl)?.filePathIfRunningCurrentFile != null }
      return !runningDescriptors.isEmpty()
    }
    else {
      val executionManager = ExecutionManagerImpl.getInstanceIfCreated(project) ?: return false
      val runningDescriptors = executionManager.getRunningDescriptors { it.isOfSameType(selectedConfiguration) }
      return !runningDescriptors.isEmpty()
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)
    val data = presentation.getClientProperty(runToolbarDataKey) ?: return
    val dataPropertyName = "old-run-toolbar-data"
    val oldData = component.getClientProperty(dataPropertyName) as? Boolean
    if (oldData == null) {
      component.putClientProperty(dataPropertyName, data)
    }
    else if (data != oldData) {
      component.repaint()
      component.putClientProperty(dataPropertyName, data)
    }
  }
}

@ApiStatus.Internal
class RunToolbarTopLevelExecutorActionGroup : ActionGroup() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    e ?: return emptyArray()
    return arrayOf(
      e.actionManager.getAction(IdeActions.ACTION_DEFAULT_RUNNER),
      e.actionManager.getAction(IdeActions.ACTION_DEFAULT_DEBUGGER)
    )
  }
}

private class PreparedIcon(private val width: Int, private val height: Int, private val iconFn: () -> Icon) : RetrievableIcon {
  constructor(icon: Icon) : this(icon.iconWidth, icon.iconHeight, { icon })

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    iconFn().paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int = width

  override fun getIconHeight(): Int = height

  override fun retrieveIcon(): Icon = iconFn()

  override fun replaceBy(replacer: IconReplacer): Icon {
    val res = replacer.replaceIcon(iconFn())
    return PreparedIcon(res.iconWidth, res.iconHeight, { res })
  }
}

private class RunWidgetButtonLook : HeaderToolbarButtonLook(
  iconSize = { 16 }
) {
  override fun getStateBackground(component: JComponent, state: Int): Color? {
    val isDisabled = (component as? ActionButton)?.presentation?.isEnabled == false
    val isStopButton = isStopButton(component)
    if (isDisabled || (!isStopButton && !buttonIsRunning(component))) {
      return super.getStateBackground(component, state)
    }

    val color = if (isStopButton) JBUI.CurrentTheme.RunWidget.STOP_BACKGROUND else JBUI.CurrentTheme.RunWidget.RUNNING_BACKGROUND

    return when (state) {
      ActionButtonComponent.NORMAL, ActionButtonComponent.SELECTED -> color
      ActionButtonComponent.PUSHED -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.PRESSED_BACKGROUND, color)
      else -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.HOVER_BACKGROUND, color)
    }
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val iconPos = getIconPosition(actionButton, icon)
    paintIcon(g, actionButton, icon, iconPos.x, iconPos.y)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon, x: Int, y: Int) {
    actionButton?: return
    if (icon.iconWidth == 0 || icon.iconHeight == 0) {
      return
    }

    if (actionButton is ActionButton && actionButton.action is RedesignedRunConfigurationSelector) {
      super.paintIcon(g, actionButton, icon, x, y)
      return
    }

    var resultIcon = icon

    if (icon is LayeredIcon && icon.allLayers.size == 2) {
      val textIcon = icon.allLayers[1]
      if (textIcon is TextIcon) {
        val text = textIcon.text
        val provider = BadgeRectProvider(top = 0.45, left = if (text.length == 1) 0.75 else 0.3, right = 1.2, bottom = 1.2)
        resultIcon = TextHoledIcon(icon = icon.allLayers[0]!!,
                                   text = text,
                                   fontSize = JBUIScale.scale(12.0f),
                                   plainColor = JBUI.CurrentTheme.RunWidget.RUNNING_ICON_COLOR,
                                   provider = provider)
      }
    }

    if (resultIcon is EmptyIcon) {
      return
    }
    else if (resultIcon !is PreparedIcon) {
      val executionAction = isRunWidgetExecutionAction(actionButton)
      val iconWithBackground = executionAction && buttonIsRunning(actionButton) || isStopButton(actionButton)
      resultIcon = toStrokeIcon(icon = resultIcon, resultColor = when {
        iconWithBackground -> JBUI.CurrentTheme.RunWidget.RUNNING_ICON_COLOR
        executionAction -> JBUI.CurrentTheme.RunWidget.RUN_ICON_COLOR
        else -> JBUI.CurrentTheme.RunWidget.ICON_COLOR
      })
    }

    paintIconImpl(g, actionButton, resultIcon, x, y)
  }
}

internal const val MINIMAL_POPUP_WIDTH = 310
internal const val MAXIMAL_POPUP_WIDTH = 500

@ApiStatus.Internal
abstract class TogglePopupAction : ToggleAction {

  constructor()

  constructor(@NlsActions.ActionText text: String?,
              @NlsActions.ActionDescription description: String?,
              icon: Icon?) : super(text, description, icon)

  override fun isSelected(e: AnActionEvent): Boolean {
    return Toggleable.isSelected(e.presentation)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!state) return
    val component = e.inputEvent?.component as? JComponent ?: return
    val start = IdeEventQueue.getInstance().popupTriggerTime
    val popup = createPopup(e) ?: return
    PopupShowingTimeTracker.showElapsedMillisIfConfigured(start, popup)
    popup.showUnderneathOf(component)
  }

  fun createPopup(e: AnActionEvent): JBPopup? {
    val presentation = e.presentation
    val actionGroup = getActionGroup(e) ?: return null
    val disposeCallback = { Toggleable.setSelected(presentation, false) }
    val popup = createPopup(actionGroup, e, disposeCallback)
    popup.setMinimumSize(JBDimension(MINIMAL_POPUP_WIDTH, 0))
    return popup
  }

  open fun createPopup(actionGroup: ActionGroup,
                       e: AnActionEvent,
                       disposeCallback: () -> Unit) = JBPopupFactory.getInstance().createActionGroupPopup(
    null, actionGroup, e.dataContext, false, false, false, disposeCallback, 30, null)

  abstract fun getActionGroup(e: AnActionEvent): ActionGroup?
}

internal abstract class WindowHeaderPlaceholder : DecorativeElement(), DumbAware, CustomComponentAction {
  private val NOT_FIRST_UPDATE = Key.create<Boolean>("notFirstUpdate")
  private val PROJECT = Key.create<Project>("justProject")

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.putClientProperty(PROJECT, e.project)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    if (presentation.getClientProperty(NOT_FIRST_UPDATE) == true) {
      return
    }
    val project = presentation.getClientProperty(PROJECT) ?: return
    presentation.putClientProperty(NOT_FIRST_UPDATE, true)

    val frameHelper = (WindowManager.getInstance() as WindowManagerImpl).getFrameHelper(project) ?: return
    CustomWindowHeaderUtil.makeComponentToBeMouseTransparentInTitleBar(frameHelper, component)
  }
}

internal class InactiveStopActionPlaceholder : WindowHeaderPlaceholder() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = EmptyIcon.ICON_16
    if (StopAction.getActiveStoppableDescriptors(e.project).isEmpty()) {
      e.presentation.isEnabled = false
      e.presentation.isVisible = true
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButton(this, presentation, ActionPlaces.NEW_UI_RUN_TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun addNotify() {
        val toolbar = ActionToolbar.findToolbarBy(this)
        if (toolbar is ActionToolbarImpl) {
          setMinimumButtonSize(toolbar.minimumButtonSizeSupplier)
        }
        super.addNotify()
      }
    }
  }
}

internal class MoreRunToolbarActions : TogglePopupAction(
  IdeBundle.message("inline.actions.more.actions.text"), null, AllIcons.Actions.More
), DumbAware {
  override fun getActionGroup(e: AnActionEvent): ActionGroup? {
    val project = e.project ?: return null
    val parentGroup = ActionToolbar.findToolbarBy(e.inputEvent?.source as? JComponent)?.actionGroup
    val exclude = executorFilterByParentGroupFactory(parentGroup)
    val selectedConfiguration = RunManager.getInstance(project).selectedConfiguration
    val result = when {
      selectedConfiguration != null -> {
        object : RunConfigurationsComboBoxAction.SelectConfigAction(project, selectedConfiguration) {
          override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
            val additionalGroup = AdditionalRunningOptions.getInstance(project).getAdditionalActions(configuration, true)
            return (listOf(additionalGroup) + getDefaultChildren(exclude(e))).toTypedArray()
          }
        }
      }
      RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project) -> {
        object : RunConfigurationsComboBoxAction.RunCurrentFileAction() {
          override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
            val additionalGroup = AdditionalRunningOptions.getInstance(project).getAdditionalActions(null, true)
            return (listOf(additionalGroup) + getDefaultChildren(exclude(e))).toTypedArray()
          }
        }
      }
      else -> object : ActionGroup(), DumbAware {
        override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
          return arrayOf(AdditionalRunningOptions.getInstance(project).getAdditionalActions(null, true))
        }
      }
    }
    return result
  }

  override fun createPopup(actionGroup: ActionGroup, e: AnActionEvent, disposeCallback: () -> Unit): ListPopup {
    val selectedConfiguration = e.project?.let { RunManager.getInstanceIfCreated(it) }?.selectedConfiguration
    val event = e.withDataContext(CustomizedDataContext.withSnapshot(e.dataContext) { sink ->
      sink[RUN_CONFIGURATION_KEY] = selectedConfiguration
    })
    return super.createPopup(actionGroup, event, disposeCallback).also {
      (it.listStep as ActionPopupStep).setSubStepContextAdjuster { context, _ ->
        CustomizedDataContext.withSnapshot(context) { sink ->
          sink[RUN_CONFIGURATION_KEY] = selectedConfiguration
        }
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

internal fun filterOutRunIfDebugResumeIsPresent(e: AnActionEvent, actions: List<AnAction>): List<AnAction> {
  val hasPause = actions.any {
    it.javaClass.simpleName.let { it == "InlineXDebuggerResumeAction" || it == "ConfigurationXDebuggerResumeAction" } ||
    e.actionManager.getId(it)?.contains("XDebuggerResumeAction") == true
  }
  val hasInlineStop = actions.any {
    it.javaClass.simpleName.let { it == "StopConfigurationInlineAction" }
  }
  return when {
    hasPause -> actions.filter { ((it as? ExecutorAction)?.id ?: e.actionManager.getId(it)) != "Run" }
    hasInlineStop -> actions.filter { ((it as? ExecutorAction)?.id ?: e.actionManager.getId(it)) != "Debug" }
    else -> actions
  }
}

internal fun executorFilterByParentGroupFactory(parentGroup: ActionGroup?): (AnActionEvent?) -> Predicate<Executor>? {
  return { event ->
    if (event == null || parentGroup == null) {
      null
    }
    else {
      val set = event.updateSession.expandedChildren(parentGroup)
        .filterIsInstance<ActionIdProvider>()
        .map { it.id }
        .toSet()
      Predicate { !set.contains(it.id) }
    }
  }
}

@ApiStatus.Internal
open class RedesignedRunConfigurationSelector : TogglePopupAction(), CustomComponentAction, DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    if (e.inputEvent != null && e.inputEvent!!.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0) {
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS).actionPerformed(e)
      return
    }
    super.actionPerformed(e)
  }

  override fun getActionGroup(e: AnActionEvent): ActionGroup? {
    return ActionManager.getInstance().getAction("RunConfigurationsActionGroup") as? ActionGroup
  }

  override fun createPopup(actionGroup: ActionGroup, e: AnActionEvent, disposeCallback: () -> Unit): ListPopup {
    val inputEventComponent = e.inputEvent?.component
    val component = e.getData(IdeFrame.KEY)?.component ?: inputEventComponent!!
    val dataContext = DataManager.getInstance().getDataContext(component)
    val result = RunConfigurationsActionGroupPopup(actionGroup, dataContext, disposeCallback)
    if (inputEventComponent != null && inputEventComponent.width > result.maxWidth) {
      // the invoking button is huge, a long configuration is selected, so relax the limit on the popup as well
      result.maxWidth = inputEventComponent.width
      // this will force the popup to take its owner size, otherwise it may expand beyond reason if there are very long lines
      result.setStretchToOwnerWidth(true)
    }
    return result
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val delegate = e.actionManager.getAction("RunConfiguration") as? RunConfigurationsComboBoxAction ?: return
    delegate.update(e)
    val configurationName = e.project?.let { RunManager.getInstanceIfCreated(it) }?.selectedConfiguration?.name
    if (configurationName != null) {
      // Replace the maybe-cut-off name (set by delegate.update) with the full one, the UI will then cut it as needed.
      e.presentation.setText(configurationName, false)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun displayTextInToolbar() = true

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return RedesignedRunConfigurationSelectorButton(this, presentation, place)
  }
}

private class RedesignedRunConfigurationSelectorButton(
  action: AnAction,
  presentation: Presentation,
  place: String,
) : ActionButtonWithText(action, presentation, place, {
  JBUI.size(16, JBUI.CurrentTheme.RunWidget.toolbarHeight())
}) {
  
  private var isTrimmed = false
  private var lastMaxTextWidth: Int? = null
  private var lastFullText: @NlsActions.ActionText String? = null
  private var lastSmartlyTrimmedTextWidth: Int = 0
  private lateinit var lastSmartlyTrimmedText: @NlsActions.ActionText String

  init {
    foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
    setHorizontalTextAlignment(SwingConstants.LEFT)
    updateFont()
  }

  override fun getMargins(): Insets = JBInsets(0, 10, 0, 6)
  override fun iconTextSpace(): Int = ToolbarComboWidgetUiSizes.gapAfterLeftIcons
  override fun shallPaintDownArrow() = true
  override fun getDownArrowIcon(): Icon = PreparedIcon(super.getDownArrowIcon())

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleActionButton() {
        override fun getAccessibleName(): String {
          val description = ExecutionBundle.message("choose.run.configuration.action.new.ui.button.description")
          return myPresentation.text?.takeIf { it.isNotBlank() }?.let { "$it, $description" } ?: description
        }
      }
    }
    return accessibleContext
  }

  override fun updateUI() {
    super.updateUI()
    updateFont()
  }

  override fun updateToolTipText() {
    // we provide our own
  }

  override fun getToolTipText(): String {
    return if (isTrimmed) {
      ExecutionBundle.message("choose.run.configuration.action.new.ui.button.description.long",
                                     StringUtil.escapeXmlEntities(text))
    }
    else {
      ExecutionBundle.message("choose.run.configuration.action.new.ui.button.description")
    }
  }

  override fun layout(
    fm: FontMetrics,
    fullText: @NlsActions.ActionText String,
    icon: Icon?,
    inViewRect: Rectangle,
    outIconRect: Rectangle,
    outTextRect: Rectangle,
  ): @NlsActions.ActionText String {
    val effectiveFM = GraphicsUtil.fontMetrics(fm.font)
    super.layout(effectiveFM, fullText, icon, inViewRect, outIconRect, outTextRect)
    if (fullText.isEmpty()) { // to avoid silly edge-case errors
      isTrimmed = false
      return fullText
    }
    // We need to recalculate this, because super.layout() is very inaccurate in some environments (e.g., macOS),
    // as it assumes that the string width is equal to the sum of character widths, which isn't always the case.
    val maxTextWidth = inViewRect.width - (outIconRect.width + if (icon == null) 0 else iconTextSpace())
    val fullTextWidth = effectiveFM.stringWidth(fullText)
    if (fullTextWidth <= maxTextWidth) { // nothing to trim, enough space
      isTrimmed = false
      outTextRect.width = fullTextWidth
      return fullText
    }
    isTrimmed = true
    if (lastFullText == fullText && lastMaxTextWidth == maxTextWidth) { // no need to recompute
      outTextRect.width = lastSmartlyTrimmedTextWidth
      return lastSmartlyTrimmedText 
    }
    val smartlyTrimmedText = trimRunConfigurationName(fullText, maxTextWidth, effectiveFM)
    lastMaxTextWidth = maxTextWidth
    lastFullText = fullText
    lastSmartlyTrimmedText = smartlyTrimmedText
    outTextRect.width = effectiveFM.stringWidth(smartlyTrimmedText)
    lastSmartlyTrimmedTextWidth = outTextRect.width
    return smartlyTrimmedText
  }

  fun updateFont() {
    font = JBUI.CurrentTheme.RunWidget.configurationSelectorFont()
    lastMaxTextWidth = null
    lastFullText = null
  }

  override fun getButtonRect(): Rectangle? = super.buttonRect.apply {
    width -= getDownArrowIcon().iconWidth
  }

  override fun getMinimumSize(): Dimension = preferredSize.apply {
    width = UIUtil.computeTextComponentMinimumSize(width, text, font?.let { getFontMetrics(it) }, 5, 0, ELLIPSIS)
  }
}

internal fun trimRunConfigurationName(fullText: @NlsActions.ActionText String, maxWidth: Int, fm: FontMetrics): @NlsActions.ActionText String {
  // more readable this way, as we're operating with lengths, not indices here
  @Suppress("ReplaceRangeToWithRangeUntil", "ReplaceManualRangeWithIndicesCalls")
  val availableLengths = (0..fullText.length - 1).toList()
  // The binary search absolutely requires a sorted input, so we can't include fullText.length in the search,
  // as the full string can be shorter than the string with one character removed and replaced with the ellipsis.
  val perfectLength = availableLengths.binarySearch { length ->
    val prefixSuffix = splitCharCount(length)
    val cutText = fullText.cutText(prefixSuffix)
    val cutTextWidth = fm.stringWidth(cutText)
    cutTextWidth.compareTo(maxWidth)
  }
  val finalLength = when {
    perfectLength >= 0 -> perfectLength
    else -> ((-perfectLength - 1) - 1).coerceIn(0, fullText.length) // here, it's safe to include the full length
  }
  return fullText.cutText(splitCharCount(finalLength))
}

@Suppress("HardCodedStringLiteral") // all this substring() stuff is NLS-safe 
private fun String.cutText(prefixSuffix: PrefixSuffix): @NlsActions.ActionText String {
  if (prefixSuffix.prefix + prefixSuffix.suffix >= length) return this // not really reachable under reasonable circumstances
  return substring(0, prefixSuffix.prefix) + ELLIPSIS + substring(length - prefixSuffix.suffix)
}

private fun splitCharCount(count: Int): PrefixSuffix {
  // Rule 1: if there are more than 8 characters available at both ends, those extra chars go to the beginning.
  // Rule 2: if both can't get 8 characters, the end is truncated first.
  // Rule 3: only when the end is gone completely, the beginning starts getting truncated to less than 8 chars.
  // Rule 4: we should keep at least 5 chars in the beginning. Not really enforceable here, but hopefully getMinimumSize() takes care of that.
  return when {
    count >= 16 -> PrefixSuffix(count - 8, 8)
    count > 8 -> PrefixSuffix(8, count - 8)
    else -> PrefixSuffix(count, 0)
  }
}

private data class PrefixSuffix(val prefix: Int, val suffix: Int)

private fun isRunWidgetExecutionAction(component: Any): Boolean {
  return getExecutionActionStatus(component) != null
}

private fun buttonIsRunning(component: Any): Boolean {
  return getExecutionActionStatus(component) == ExecutorActionStatus.RUNNING
}

private fun getExecutionActionStatus(component: Any): ExecutorActionStatus? {
  return (component as? ActionButton)?.presentation?.getClientProperty(ExecutorActionStatus.KEY)
}

private fun isStopButton(component: Any): Boolean {
  val action = (component as? ActionButton)?.action ?: return false
  return action is StopAction || ActionManager.getInstance().getId(action) == IdeActions.ACTION_STOP_PROGRAM
}