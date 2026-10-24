// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.util.Collections
import java.util.function.Consumer
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.SwingUtilities

@ApiStatus.Internal
abstract class PluginsGroupComponent @RequiresEdt constructor(eventHandler: EventHandler) : JBPanelWithEmptyText(PluginListLayout()) {

  private val myEventHandler: EventHandler = eventHandler
  private val myGroups: MutableList<UIPluginGroup> = ArrayList()

  init {
    myEventHandler.connect(this)

    setOpaque(true)
    setBackground(PluginManagerConfigurable.MAIN_BG_COLOR)

    setFocusTraversalPolicyProvider(true)
    // Focus traversal policy that makes focus order similar to lists and trees, where Tab doesn't move focus between list items,
    // but instead moves focus to the next component. It also keeps group header buttons and buttons inside list items focusable.
    setFocusTraversalPolicy(object : ComponentsListFocusTraversalPolicy(true) {
      override fun getOrderedComponents(): List<Component> {
        val orderedComponents: MutableList<Component> = ArrayList()
        val selectedComponents: List<ListPluginComponent> = selection
        val addedGroups: MutableSet<PluginsGroup> = HashSet()

        for (component in selectedComponents) {
          val group = component.getGroup()
          if (!addedGroups.contains(group)) {
            addedGroups.add(group)
            if (UIUtil.isFocusable(group.mainAction)) {
              orderedComponents.add(group.mainAction!!)
            }
            else if (!ContainerUtil.isEmpty(group.secondaryActions)) {
              for (action in group.secondaryActions!!) {
                if (UIUtil.isFocusable(action)) {
                  orderedComponents.add(action)
                }
              }
            }
          }

          orderedComponents.add(component)
          orderedComponents.addAll(component.getFocusableComponents())
        }

        return orderedComponents
      }
    })
  }

  protected abstract fun createListComponent(model: PluginUiModel, group: PluginsGroup, listPluginModel: ListPluginModel): ListPluginComponent

  val groups: List<UIPluginGroup>
    get() = Collections.unmodifiableList(myGroups)

  fun setSelectionListener(listener: Consumer<in PluginsGroupComponent?>?) {
    myEventHandler.setSelectionListener(listener)
  }

  val selection: List<ListPluginComponent>
    get() = myEventHandler.getSelection()

  fun setSelection(component: ListPluginComponent) {
    myEventHandler.setSelection(component)
  }

  fun setSelection(components: List<ListPluginComponent>) {
    myEventHandler.setSelection(components)
  }

  fun addGroup(group: PluginsGroup) {
    addGroup(group, -1)
  }

  fun addGroup(group: PluginsGroup, groupIndex: Int) {
    addGroup(group, group.getModels(), groupIndex)
  }

  fun addLazyGroup(group: PluginsGroup, scrollBar: JScrollBar, gapSize: Int, uiCallback: Runnable) {
    if (group.getModels().size <= gapSize) {
      addGroup(group)
    }
    else {
      addGroup(group, group.getModels().subList(0, gapSize), -1)
      val listener = object : AdjustmentListener {
        override fun adjustmentValueChanged(e: AdjustmentEvent) {
          if ((scrollBar.value + scrollBar.visibleAmount) >= scrollBar.maximum) {
            val uiGroup = group.ui!!
            val fromIndex = uiGroup.plugins.size
            val toIndex = Math.min(fromIndex + gapSize, group.getModels().size)
            val lastComponent = uiGroup.plugins[fromIndex - 1]
            val uiIndex = getComponentIndex(lastComponent)
            val eventIndex = myEventHandler.getCellIndex(lastComponent)
            try {
              PluginLogo.startBatchMode()
              addToGroup(group, group.getModels().subList(fromIndex, toIndex), uiIndex, eventIndex)
            }
            finally {
              PluginLogo.endBatchMode()
            }

            if (group.getModels().size == uiGroup.plugins.size) {
              scrollBar.removeAdjustmentListener(this)
              group.clearCallback = null
            }

            uiCallback.run()
          }
        }
      }
      group.clearCallback = Runnable { scrollBar.removeAdjustmentListener(listener) }
      scrollBar.addAdjustmentListener(listener)
    }
  }

  @Suppress("UseHtmlChunkToolTip")
  private fun addGroup(group: PluginsGroup, models: List<PluginUiModel>, groupIndex: Int) {
    val uiGroup = UIPluginGroup()
    group.ui = uiGroup
    if (Registry.`is`("ide.plugins.category.promotion.enabled") && group.promotionPanel != null) {
      uiGroup.promotionPanel = group.promotionPanel
    }
    myGroups.add(if (groupIndex == -1) myGroups.size else groupIndex, uiGroup)

    val panel: OpaquePanel = object : OpaquePanel(BorderLayout(), SECTION_HEADER_BACKGROUND) {
      override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
          accessibleContext = AccessibleOpaquePanelComponent()
        }
        return accessibleContext
      }

      protected inner class AccessibleOpaquePanelComponent : AccessibleJComponent() {
        override fun getAccessibleName(): String {
          return group.title
        }

        override fun getAccessibleRole(): AccessibleRole {
          return AccessibilityUtils.GROUPED_ELEMENTS
        }
      }
    }
    panel.setBorder(JBUI.Borders.empty(4, 10))

    val title: JLabel = object : JLabel(group.title) {
      override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        val parent: Container = getParent()
        val insets: Insets = parent.insets
        size.width = Math.min(
          parent.width - insets.left - insets.right -
          (if (parent.componentCount == 2) parent.getComponent(1).width + JBUIScale.scale(20) else 0),
          size.width,
        )
        return size
      }

      override fun getToolTipText(): String? {
        return if (super.getPreferredSize().width > width) super.getToolTipText() else null
      }
    }
    title.toolTipText = group.title
    title.foreground = SECTION_HEADER_FOREGROUND
    panel.add(title, BorderLayout.WEST)
    group.titleLabel = title

    if (group.mainAction != null) {
      panel.add(group.mainAction!!, BorderLayout.EAST)
    }
    else if (!ContainerUtil.isEmpty(group.secondaryActions)) {
      val actions: JPanel = NonOpaquePanel(HorizontalLayout(JBUIScale.scale(5)))
      panel.add(actions, BorderLayout.EAST)

      for (action in group.secondaryActions!!) {
        actions.add(action)
      }
    }

    var index: Int
    val eventIndex: Int

    if (groupIndex == 0) {
      add(panel, 0)
      index = 1
      eventIndex = 0
    }
    else if (groupIndex == -1) {
      add(panel)
      index = -1
      eventIndex = -1
    }
    else {
      assert(groupIndex < myGroups.size)
      index = getComponentIndex(myGroups[groupIndex + 1].panel!!)
      assert(index != -1)
      add(panel, index++)

      eventIndex = getEventIndexForGroup(groupIndex + 1)
    }

    uiGroup.panel = panel

    if (Registry.`is`("ide.plugins.category.promotion.enabled")) {
      if (uiGroup.promotionPanel != null) {
        if (index == -1) {
          add(uiGroup.promotionPanel!!)
        }
        else {
          add(uiGroup.promotionPanel!!, index)
          index++
        }
      }
    }

    addToGroup(group, models, index, eventIndex)
  }

  private fun getEventIndexForGroup(groupIndex: Int): Int {
    for (i in groupIndex downTo 0) {
      val plugins = myGroups[i].plugins
      if (!plugins.isEmpty()) {
        return myEventHandler.getCellIndex(plugins[0])
      }
    }
    return -1
  }

  private fun addToGroup(group: PluginsGroup, models: List<PluginUiModel>, index: Int, eventIndex: Int) {
    var index = index
    var eventIndex = eventIndex
    val uiGroup = group.ui!!
    for (pluginUiModel in models) {
      val pluginComponent = createListComponent(pluginUiModel, group, group.getPreloadedModel())
      uiGroup.plugins.add(pluginComponent)
      add(pluginComponent, index)
      myEventHandler.addCell(pluginComponent, eventIndex)
      pluginComponent.setListeners(myEventHandler)
      if (index != -1) {
        index++
      }
      if (eventIndex != -1) {
        eventIndex++
      }
    }
  }

  fun addToGroup(group: PluginsGroup, model: PluginUiModel) {
    val index = group.addWithIndex(model)
    var anchor: ListPluginComponent? = null
    var uiIndex = -1

    val uiGroup = group.ui!!
    val plugins = uiGroup.plugins
    if (index == plugins.size) {
      val groupIndex = myGroups.indexOf(uiGroup)
      if (groupIndex < myGroups.size - 1) {
        val nextGroup = myGroups[groupIndex + 1]
        anchor = nextGroup.plugins[0]
        uiIndex = getComponentIndex(nextGroup.panel!!)
      }
    }
    else {
      anchor = plugins[index]
      uiIndex = getComponentIndex(anchor)
    }

    val pluginComponent = createListComponent(model, group, group.getPreloadedModel())
    plugins.add(index, pluginComponent)
    add(pluginComponent, uiIndex)
    myEventHandler.addCell(pluginComponent, anchor)
    pluginComponent.setListeners(myEventHandler)
  }

  fun removeGroup(group: PluginsGroup) {
    val uiGroup = group.ui!!
    myGroups.remove(uiGroup)
    remove(uiGroup.panel!!)

    for (plugin in uiGroup.plugins) {
      plugin.close()
      remove(plugin)
      myEventHandler.removeCell(plugin)
    }

    myEventHandler.updateSelection()
    group.clear()
  }

  fun removeFromGroup(group: PluginsGroup, descriptor: PluginUiModel) {
    val uiGroup = group.ui!!
    val index = ContainerUtil.indexOf(uiGroup.plugins) { component -> component.getPluginModel() === descriptor }
    assert(index != -1)
    val component = uiGroup.plugins.removeAt(index)
    component.close()
    remove(component)
    myEventHandler.removeCell(component)
    if (component.getSelection() == EventHandler.SelectionType.SELECTION) {
      myEventHandler.updateSelection()
    }
    group.removeDescriptor(descriptor)
  }

  private fun getComponentIndex(component: Component): Int {
    val components = componentCount
    for (i in 0 until components) {
      if (getComponent(i) === component) {
        return i
      }
    }
    return -1
  }

  open fun clear() {
    for (group in myGroups) {
      for (plugin in group.plugins) {
        plugin.close()
      }
    }

    myGroups.clear()
    myEventHandler.clear()
    removeAll()
  }

  fun initialSelection() {
    initialSelection(true)
  }

  fun initialSelection(scrollAndFocus: Boolean) {
    SwingUtilities.invokeLater {
      myEventHandler.initialSelection(scrollAndFocus)
      if (!myGroups.isEmpty()) {
        scrollRectToVisible(myGroups[0].panel!!.bounds)
      }
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessiblePluginsGroupComponent()
    }
    return accessibleContext
  }

  @Suppress("RedundantInnerClassModifier")
  protected inner class AccessiblePluginsGroupComponent : AccessibleJComponent() {
    override fun getAccessibleRole(): AccessibleRole {
      return AccessibilityUtils.GROUPED_ELEMENTS
    }
  }

  companion object {
    @JvmField
    val SECTION_HEADER_FOREGROUND: Color =
      JBColor.namedColor("Plugins.SectionHeader.foreground", JBColor(0x787878, 0x999999))

    private val SECTION_HEADER_BACKGROUND: Color =
      JBColor.namedColor("Plugins.SectionHeader.background", JBColor(0xF7F7F7, 0x3C3F41))
  }
}
