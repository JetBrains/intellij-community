// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.*
import com.intellij.ui.SimpleTextAttributes.StyleAttributeConstant
import com.intellij.ui.content.AlertIcon
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseEvent
import java.beans.PropertyChangeSupport
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent

class TabInfo(var component: JComponent) : Queryable, PlaceProvider {
  companion object {
    const val ACTION_GROUP: String = "actionGroup"
    const val ICON: String = "icon"
    const val TAB_COLOR: String = "color"
    const val COMPONENT: String = "component"
    const val TEXT: String = "text"
    const val TAB_ACTION_GROUP: String = "tabActionGroup"
    const val ALERT_ICON: String = "alertIcon"

    const val ALERT_STATUS: String = "alertStatus"
    const val HIDDEN: String = "hidden"
    const val ENABLED: String = "enabled"

    private val DEFAULT_ALERT_ICON = AlertIcon(AllIcons.Nodes.TabAlert, 0, -JBUI.scale(6))
  }

  @JvmField
  internal var tabLabel: TabLabel? = null

  private var preferredFocusableComponent: JComponent? = component

  var group: ActionGroup? = null
    private set

  val changeSupport: PropertyChangeSupport = PropertyChangeSupport(this)

  var icon: Icon? = null
    private set

  private var place: @NonNls String? = null

  var `object`: Any? = null
    private set

  var sideComponent: JComponent? = null
    private set

  var foreSideComponent: JComponent? = null
    private set

  private var lastFocusOwnerRef: Reference<JComponent>? = null

  var lastFocusOwner: JComponent?
    get() = lastFocusOwnerRef?.get()
    set(value) {
      lastFocusOwnerRef = value?.let { WeakReference(it) }
    }

  var tabLabelActions: ActionGroup? = null
    private set

  var tabPaneActions: ActionGroup? = null
    private set

  var tabActionPlace: String? = null
    private set

  private var alertIcon: AlertIcon? = null

  var blinkCount: Int = 0

  var isAlertRequested: Boolean = false
    private set

  var isHidden: Boolean = false
    set(hidden) {
      val old = field
      field = hidden
      changeSupport.firePropertyChange(HIDDEN, old, field)
    }

  var actionsContextComponent: JComponent? = null
    private set

  val coloredText: SimpleColoredText = SimpleColoredText()

  var tooltipText: @NlsContexts.Tooltip String? = null
    private set

  private var defaultStyle = -1

  var defaultForeground: Color? = null
    private set

  @Internal
  var editorAttributes: TextAttributes? = null
    private set

  private var defaultAttributes: SimpleTextAttributes? = null

  var isEnabled: Boolean = true
    set(enabled) {
      val old = field
      field = enabled
      changeSupport.firePropertyChange(ENABLED, old, field)
    }

  var tabColor: Color? = null
    private set

  private var queryable: Queryable? = null

  var dragOutDelegate: DragOutDelegate? = null
    private set

  var dragDelegate: DragDelegate? = null

  /**
   * The tab which was selected before the mouse was pressed on this tab. Focus will be transferred to that tab if this tab is dragged
   * out of its container. (IDEA-61536)
   */
  private var previousSelectionRef: WeakReference<TabInfo>? = null

  var previousSelection: TabInfo?
    get() = previousSelectionRef?.get()
    set(value) {
      previousSelectionRef = value?.let { WeakReference(it) }
    }

  fun setText(text: @NlsContexts.TabTitle String): TabInfo {
    val attributes = coloredText.attributes
    val textAttributes = attributes.singleOrNull()?.toTextAttributes()
    val defaultAttributes = getDefaultAttributes()
    if (coloredText.toString() != text || textAttributes != defaultAttributes.toTextAttributes()) {
      clearText(false)
      @Suppress("DialogTitleCapitalization")
      append(text, defaultAttributes)
    }
    return this
  }

  @Internal
  fun getFontSize(): Int {
    if (tabLabel == null) {
      return 0
    }
    else {
      return tabLabel!!.font.size
    }
  }

  private fun getDefaultAttributes(): SimpleTextAttributes {
    if (defaultAttributes == null) {
      val style = ((if (defaultStyle == -1) SimpleTextAttributes.STYLE_PLAIN else defaultStyle)
        or SimpleTextAttributes.STYLE_USE_EFFECT_COLOR)
      if (editorAttributes == null) {
        defaultAttributes = SimpleTextAttributes(style, defaultForeground)
      }
      else {
        val attr = SimpleTextAttributes.fromTextAttributes(editorAttributes)
        defaultAttributes = SimpleTextAttributes.merge(SimpleTextAttributes(style, defaultForeground), attr)
      }
    }
    return defaultAttributes!!
  }

  fun clearText(invalidate: Boolean): TabInfo {
    val old = coloredText.toString()
    coloredText.clear()
    if (invalidate) {
      changeSupport.firePropertyChange(TEXT, old, coloredText.toString())
    }
    return this
  }

  fun append(fragment: @NlsContexts.Label String, attributes: SimpleTextAttributes): TabInfo {
    val old = coloredText.toString()
    coloredText.append(fragment, attributes)
    changeSupport.firePropertyChange(TEXT, old, coloredText.toString())
    return this
  }

  fun setIcon(icon: Icon?): TabInfo {
    val old = this.icon
    if (old != icon) {
      this.icon = icon
      changeSupport.firePropertyChange(ICON, old, icon)
    }
    return this
  }

  fun setComponent(c: Component): TabInfo {
    if (component !== c) {
      val old = component
      component = c as JComponent
      changeSupport.firePropertyChange(COMPONENT, old, component)
    }
    return this
  }

  val isPinned: Boolean
    get() = ClientProperty.isTrue(component, JBTabsImpl.PINNED)

  val text: @NlsContexts.TabTitle String
    get() = coloredText.toString()

  override fun getPlace(): String? = place

  fun setSideComponent(comp: JComponent?): TabInfo {
    sideComponent = comp
    return this
  }

  fun setForeSideComponent(comp: JComponent?): TabInfo {
    foreSideComponent = comp
    return this
  }

  fun setActions(group: ActionGroup?, place: @NonNls String?): TabInfo {
    val old = this.group
    this.group = group
    this.place = place
    changeSupport.firePropertyChange(ACTION_GROUP, old, this.group)
    return this
  }

  fun setActionsContextComponent(c: JComponent?): TabInfo {
    actionsContextComponent = c
    return this
  }

  fun setObject(`object`: Any?): TabInfo {
    this.`object` = `object`
    return this
  }

  fun getPreferredFocusableComponent(): JComponent = preferredFocusableComponent ?: component

  fun setPreferredFocusableComponent(component: JComponent?): TabInfo {
    preferredFocusableComponent = component
    return this
  }

  fun setTabLabelActions(tabActions: ActionGroup?, place: String): TabInfo {
    val old = tabLabelActions
    tabLabelActions = tabActions
    tabActionPlace = place
    changeSupport.firePropertyChange(TAB_ACTION_GROUP, old, tabLabelActions)
    return this
  }

  /**
   * Sets the actions that will be displayed on the right side of the tabs
   */
  fun setTabPaneActions(tabPaneActions: ActionGroup?): TabInfo {
    this.tabPaneActions = tabPaneActions
    return this
  }

  fun setAlertIcon(alertIcon: AlertIcon?): TabInfo {
    val old = this.alertIcon
    this.alertIcon = alertIcon
    changeSupport.firePropertyChange(ALERT_ICON, old, this.alertIcon)
    return this
  }

  fun fireAlert() {
    isAlertRequested = true
    changeSupport.firePropertyChange(ALERT_STATUS, null, true)
  }

  fun stopAlerting() {
    isAlertRequested = false
    changeSupport.firePropertyChange(ALERT_STATUS, null, false)
  }

  override fun toString(): String = LoadingNode.getText()

  fun getAlertIcon(): AlertIcon = alertIcon ?: DEFAULT_ALERT_ICON

  fun resetAlertRequest() {
    isAlertRequested = false
  }

  fun setDefaultStyle(@StyleAttributeConstant style: Int): TabInfo {
    defaultStyle = style
    defaultAttributes = null
    update()
    return this
  }

  fun setDefaultForeground(foregroundColor: Color?): TabInfo {
    defaultForeground = foregroundColor
    defaultAttributes = null
    update()
    return this
  }

  // avoid calling update several times
  @Internal
  fun setDefaultForegroundAndAttributes(foregroundColor: Color?, attributes: TextAttributes?) {
    defaultForeground = foregroundColor
    editorAttributes = attributes
    defaultAttributes = null
    update()
  }

  fun setDefaultAttributes(attributes: TextAttributes?): TabInfo {
    editorAttributes = attributes
    defaultAttributes = null
    update()
    return this
  }

  private fun update() {
    setText(text)
  }

  fun revalidate() {
    defaultAttributes = null
    update()
  }

  fun setTooltipText(text: @NlsContexts.Tooltip String?): TabInfo {
    val old = tooltipText
    if (old != text) {
      tooltipText = text
      changeSupport.firePropertyChange(TEXT, old, tooltipText)
    }
    return this
  }

  fun setTabColor(color: Color?): TabInfo {
    val old = tabColor
    if (color != old) {
      tabColor = color
      changeSupport.firePropertyChange(TAB_COLOR, old, color)
    }
    return this
  }

  @Internal
  fun setTestableUi(queryable: Queryable?): TabInfo {
    this.queryable = queryable
    return this
  }

  override fun putInfo(info: MutableMap<in String, in String>) {
    queryable?.putInfo(info)
  }

  fun setDragOutDelegate(delegate: DragOutDelegate?): TabInfo {
    dragOutDelegate = delegate
    return this
  }

  fun canBeDraggedOut(): Boolean = dragOutDelegate != null

  interface DragDelegate {
    fun dragStarted(mouseEvent: MouseEvent)
    fun dragFinishedOrCanceled()
  }

  interface DragOutDelegate {
    fun dragOutStarted(mouseEvent: MouseEvent, info: TabInfo)

    fun processDragOut(event: MouseEvent, source: TabInfo)

    fun dragOutFinished(event: MouseEvent, source: TabInfo)

    fun dragOutCancelled(source: TabInfo)
  }
}
