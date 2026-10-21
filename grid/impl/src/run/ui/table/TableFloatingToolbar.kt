package com.intellij.database.run.ui.table

import com.intellij.codeInsight.hint.HintUtil.getHintBorderColor
import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.SeparatorAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.checkCanceled
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.ui.HintHint
import com.intellij.ui.JBColor
import com.intellij.ui.LightweightHint
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import com.intellij.util.io.await
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.JComponent

/**
 * Shows the floating actions of a grid in a popup at a point it is given.
 * [TableFloatingToolbarController] decides when and where, from the input the tables receive.
 */
class TableFloatingToolbar internal constructor(
  private val actionTarget: JComponent,
  private val grid: DataGrid,
  private val cs: CoroutineScope,
) {

  class CustomizableGroupProvider : CustomizableActionGroupProvider() {
    override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
      registrar.addCustomizableActionGroup(ACTION_GROUP_ID, actionGroupTitle)
    }
  }

  private val actionGroup = DefaultActionGroup(
    CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as ActionGroup,
    MoreActionGroup().apply {
      (ActionManager.getInstance().getAction("Console.TableResult.FloatingToolbar.MoreGroup") as? ActionGroup)?.let {
        addAll(it)
      }
    }
  )

  private val actionToolbar = object : ActionToolbarImpl(ACTION_PLACE, actionGroup, true) {
    override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
      super.actionsUpdated(forced, newVisibleActions)
      hintUpdate()
    }
  }.apply {
    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    component.border = JBUI.Borders.empty()
    targetComponent = actionTarget
    isReservePlaceAutoPopupIcon = false
    ClientProperty.put(this, ActionToolbarImpl.SUPPRESS_FAST_TRACK, true)
  }

  private val panel = BorderLayoutPanel().addToCenter(actionToolbar.component).apply {
    border = BorderFactory.createEtchedBorder()
  }
  private val hint = LightweightHint(panel).apply {
    setForceLightweightPopup(true)
  }
  private val hintOptions: HintHint = HintHint()
    .setRequestFocus(false)
    .setTextBg(JBColor.background())
    .setBorderColor(getHintBorderColor())
    .setTextFg(JBColor.foreground())

  /** The component the popup is requested in, or null while no popup is requested. */
  private var shownIn: JComponent? = null

  /** Where the bottom-right corner of the popup goes, in [shownIn] coordinates. */
  private var shownAt = Point(0, 0)
  private val showingJob: AtomicReference<Job?> = AtomicReference(null)

  internal val isShowing: Boolean
    get() = hint.isVisible && hint.size.height != 0 && hint.size.width != 0

  /** The bounds of the popup, in the coordinates of the component it is shown in. */
  internal val bounds: Rectangle
    get() = Rectangle(Point(shownAt.x - hint.size.width, shownAt.y - hint.size.height), hint.size)

  private fun hintUpdate() {
    if (!hint.isVisible) {
      return
    }
    if (!actionToolbar.actions.any { it !is SeparatorAction && it !is MoreActionGroup }) {
      hide()
      return
    }

    hint.pack()
    hint.updateLocation(shownAt.x - hint.component.width, shownAt.y - hint.component.height)
  }

  private fun isDisabled(): Boolean = GridUtil.getSettings(grid)?.isDisableGridFloatingToolbar ?: true

  /** Shows the popup in [anchor], with its bottom-right corner at [corner], once the actions are updated. */
  internal fun show(anchor: JComponent, corner: Point) {
    if (isDisabled()) {
      return
    }

    shownIn = anchor
    shownAt = corner
    val job = cs.launch(Dispatchers.EDT) {
      ComponentUtil.markAsShowing(actionToolbar.component, true)
      val future = actionToolbar.updateActionsAsync()
      try {
        future.await()
      }
      catch (_: Exception) {
      }
      finally {
        ComponentUtil.markAsShowing(actionToolbar.component, false)
      }

      val hasVisibleAction = actionToolbar.actions.any { it !is SeparatorAction && it !is MoreActionGroup }
      if (!hasVisibleAction) {
        return@launch
      }

      checkCanceled()
      val target = shownIn ?: return@launch
      hint.show(target,
                shownAt.x - hint.component.preferredWidth,
                shownAt.y - hint.component.preferredHeight,
                target,
                hintOptions)
    }
    // A newer request replaces an update that is still pending, so the older one cannot show the popup later.
    showingJob.getAndSet(job)?.cancel()
  }

  internal fun hide() {
    showingJob.getAndSet(null)?.cancel()
    shownIn = null
    hint.hide()
  }

  /** The component and the point the popup is requested at, or null while none is requested. */
  @TestOnly
  internal fun getRequestedDisplay(): Pair<JComponent, Point>? = shownIn?.let { it to Point(shownAt) }

  companion object {
    const val ACTION_PLACE = "TableFloatingToolbar"
    const val ACTION_GROUP_ID = "Console.TableResult.FloatingToolbarGroup"
    val actionGroupTitle = DataGridBundle.message("Console.TableResult.FloatingToolbarGroup.title")
  }
}
