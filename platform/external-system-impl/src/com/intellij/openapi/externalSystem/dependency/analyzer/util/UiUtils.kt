// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.operations.ObservableOperationTrace
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.border.Border


internal const val BORDER = 6
internal const val INDENT = 16
internal const val ICON_TEXT_GAP = 4
internal const val ACTION_BORDER = 2

internal fun emptyListBorder(): Border {
  return JBUI.Borders.empty()
}

internal fun emptyListCellBorder(list: JList<*>, index: Int, indent: Int = 0): Border {
  val topGap = if (index > 0) BORDER / 2 else BORDER
  val bottomGap = if (index < list.model.size - 1) BORDER / 2 else BORDER
  val leftGap = BORDER + INDENT * indent
  val rightGap = BORDER
  return JBUI.Borders.empty(topGap, leftGap, bottomGap, rightGap)
}

internal fun setupListPopupPreferredWidth(list: JList<*>) {
  list.setPreferredWidth(maxOf(JBUI.scale(164), list.preferredSize.width))
}

internal fun JComponent.setPreferredWidth(width: Int) {
  preferredSize = preferredSize.also { it.width = width }
}

internal fun label(text: @Nls String) =
  JLabel(text)
    .apply { border = JBUI.Borders.empty(BORDER) }

internal fun label(property: ObservableProperty<@Nls String>) =
  label(property.get())
    .bind(property)

internal fun toolWindowPanel(configure: SimpleToolWindowPanel.() -> Unit) =
  SimpleToolWindowPanel(true, true)
    .apply { configure() }

internal fun toolbarPanel(configure: BorderLayoutPanel.() -> Unit) =
  BorderLayoutPanel()
    .apply { layout = BorderLayout() }
    .apply { border = JBUI.Borders.empty(1, 2) }
    .apply { withMinimumHeight(JBUI.scale(30)) }
    .apply { withPreferredHeight(JBUI.scale(30)) }
    .apply { configure() }

internal fun horizontalPanel(vararg components: JComponent) =
  JPanel()
    .apply { layout = HorizontalLayout(0) }
    .apply { border = JBUI.Borders.empty() }
    .apply { components.forEach(::add) }

internal fun horizontalSplitPanel(proportionKey: @NonNls String, proportion: Float, configure: OnePixelSplitter.() -> Unit) =
  OnePixelSplitter(false, proportionKey, proportion)
    .apply { configure() }

internal fun <T> cardPanel(createPanel: (T) -> JComponent) =
  object : CardLayoutPanel<T, T, JComponent>() {
    override fun prepare(key: T) = key
    override fun create(ui: T) = createPanel(ui)
  }

internal fun <T, C : CardLayoutPanel<T, *, *>> C.bind(property: ObservableProperty<T>): C = apply {
  select(property.get(), true)
  property.afterChange { select(it, true) }
}

internal fun <C : JBLoadingPanel> C.bind(operation: ObservableOperationTrace): C = apply {
  if (operation.isOperationCompleted()) {
    stopLoading()
  }
  else {
    startLoading()
  }
  operation.beforeOperation { startLoading() }
  operation.afterOperation { stopLoading() }
}

internal fun <C : JBLoadingPanel> C.bindLoadingText(property: ObservableProperty<@Nls String>): C = apply {
  setLoadingText(property.get())
  property.afterChange { setLoadingText(it) }
}

internal fun toggleAction(property: ObservableMutableProperty<Boolean>): ToggleAction =
  object : ToggleAction(), DumbAware {
    override fun isSelected(e: AnActionEvent) = property.get()
    override fun setSelected(e: AnActionEvent, state: Boolean) = property.set(state)
  }

internal fun action(action: (AnActionEvent) -> Unit): AnAction =
  object : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) = action(e)
  }

internal fun popupActionGroup(vararg actions: AnAction) =
  DefaultActionGroup(*actions)
    .apply { isPopup = true }

internal fun AnAction.asActionButton(place: String) =
  ActionButton(this, templatePresentation.clone(), place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    .apply { border = JBUI.Borders.empty(ACTION_BORDER) }

internal fun separator() =
  JLabel(AllIcons.General.Divider)
    .apply { border = JBUI.Borders.empty(ACTION_BORDER) }
    .apply { font = JBUI.Fonts.toolbarSmallComboBoxFont() }

internal fun expandTreeAction(tree: JTree) =
  action { TreeUtil.expandAll(tree) }
    .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.tree.expand") }
    .apply { templatePresentation.icon = AllIcons.Actions.Expandall }

internal fun collapseTreeAction(tree: JTree) =
  action { TreeUtil.collapseAll(tree, 0) }
    .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.tree.collapse") }
    .apply { templatePresentation.icon = AllIcons.Actions.Collapseall }
