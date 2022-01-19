// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle.message
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable.ICON_FLAG_READ_STATUS
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil.getFileBackgroundColor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowEventSource
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.ui.*
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.speedSearch.SpeedSearchUtil.applySpeedSearchHighlighting
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

private fun shortcutText(actionId: String) = ActionManager.getInstance().getKeyboardShortcut(actionId)?.let { getShortcutText(it) }

private val mainTextComparator by lazy { Comparator.comparing(SwitcherListItem::mainText, NaturalComparator.INSTANCE) }


internal interface SwitcherListItem {
  val mnemonic: String? get() = null
  val mainText: String
  val statusText: String get() = ""
  val shortcutText: String? get() = null
  val separatorAbove: Boolean get() = false

  fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode)
  fun close(switcher: Switcher.SwitcherPanel) = Unit

  fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean)
  fun prepareExtraRenderer(component: SimpleColoredComponent, selected: Boolean) {
    shortcutText?.let {
      component.append(it, when (selected) {
        true -> SimpleTextAttributes.REGULAR_ATTRIBUTES
        else -> SimpleTextAttributes.SHORTCUT_ATTRIBUTES
      })
    }
  }
}


internal class SwitcherRecentLocations(val switcher: Switcher.SwitcherPanel) : SwitcherListItem {
  override val separatorAbove = true
  override val mainText: String
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> message("recent.locations.changed.locations")
      else -> message("recent.locations.popup.title")
    }
  override val statusText: String
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> message("recent.files.accessible.open.recently.edited.locations")
      else -> message("recent.files.accessible.open.recently.viewed.locations")
    }
  override val shortcutText: String?
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> null
      else -> shortcutText(RecentLocationsAction.RECENT_LOCATIONS_ACTION_ID)
    }

  override fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode) {
    RecentLocationsAction.showPopup(switcher.project, switcher.isOnlyEditedFilesShown)
  }

  override fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean) {
    component.iconTextGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
    component.append(mainText)
  }
}


internal class SwitcherToolWindow(val window: ToolWindow, shortcut: Boolean) : SwitcherListItem {
  private val actionId = ActivateToolWindowAction.getActionIdForToolWindow(window.id)
  override var mnemonic: String? = null

  override val mainText = window.stripeTitle
  override val statusText = message("recent.files.accessible.show.tool.window", mainText)
  override val shortcutText = if (shortcut) shortcutText(actionId) else null

  override fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode) {
    val manager = ToolWindowManager.getInstance(switcher.project) as? ToolWindowManagerImpl
    manager?.activateToolWindow(window.id, null, true, when (switcher.isSpeedSearchPopupActive) {
      true -> ToolWindowEventSource.SwitcherSearch
      else -> ToolWindowEventSource.Switcher
    }) ?: window.activate(null, true)
  }

  override fun close(switcher: Switcher.SwitcherPanel) {
    val manager = ToolWindowManager.getInstance(switcher.project) as? ToolWindowManagerImpl
    manager?.hideToolWindow(id = window.id, hideSide = false, moveFocus = false, source = ToolWindowEventSource.CloseFromSwitcher) ?: window.hide()
  }

  override fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean) {
    val defaultIcon = if (ExperimentalUI.isNewUI()) EmptyIcon.ICON_16 else EmptyIcon.ICON_13
    component.iconTextGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
    component.icon = IconUtil.scaleByIconWidth(RenderingUtil.getIcon(window.icon, selected), null, defaultIcon)
    component.append(mainText)
  }
}


internal class SwitcherVirtualFile(
  val project: Project,
  val file: VirtualFile,
  val window: EditorWindow?
) : SwitcherListItem, BackgroundSupplier {

  private val icon by lazy { IconUtil.getIcon(file, ICON_FLAG_READ_STATUS, project) }

  val isProblemFile
    get() = WolfTheProblemSolver.getInstance(project)?.isProblemFile(file) == true

  override var mainText: String = ""

  override val statusText: String
    get() = getLocationRelativeToUserHome((file.parent ?: file).presentableUrl)

  override fun navigate(switcher: Switcher.SwitcherPanel, mode: OpenMode) {
  }

  override fun close(switcher: Switcher.SwitcherPanel) {
  }

  override fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean) {
    component.iconTextGap = JBUI.scale(4)
    component.icon = when (Registry.`is`("ide.project.view.change.icon.on.selection", true)) {
      true -> RenderingUtil.getIcon(icon, selected)
      else -> icon
    }
    val foreground = if (selected) null else FileStatusManager.getInstance(project).getStatus(file).color
    val effectColor = if (isProblemFile) JBColor.red else null
    val style = when (effectColor) {
      null -> SimpleTextAttributes.STYLE_PLAIN
      else -> SimpleTextAttributes.STYLE_PLAIN or SimpleTextAttributes.STYLE_WAVED
    }
    component.append(mainText, SimpleTextAttributes(style, foreground, effectColor))
  }

  override fun getElementBackground(row: Int) = getFileBackgroundColor(project, file)
}


internal class SwitcherListRenderer(val switcher: Switcher.SwitcherPanel) : ListCellRenderer<SwitcherListItem> {
  private val SEPARATOR = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
  @Suppress("HardCodedStringLiteral") // to calculate preferred size
  private val mnemonic = JLabel("W").apply {
    preferredSize = preferredSize.apply { width += JBUI.CurrentTheme.ActionsList.mnemonicIconGap() }
    font = JBUI.CurrentTheme.ActionsList.applyStylesForNumberMnemonic(font)
  }
  private val main = SimpleColoredComponent().apply { isOpaque = false }
  private val extra = SimpleColoredComponent().apply { isOpaque = false }
  private val panel = CellRendererPanel(BorderLayout()).apply {
    add(BorderLayout.WEST, mnemonic)
    add(BorderLayout.CENTER, main)
    add(BorderLayout.EAST, extra)
  }

  override fun getListCellRendererComponent(list: JList<out SwitcherListItem>, value: SwitcherListItem, index: Int,
                                            selected: Boolean, focused: Boolean): Component {
    main.clear()
    extra.clear()

    val border = JBUI.Borders.empty(0, 10)
    panel.border = when (!selected && value.separatorAbove) {
      true -> JBUI.Borders.compound(border, JBUI.Borders.customLine(SEPARATOR, 1, 0, 0, 0))
      else -> border
    }
    RenderingUtil.getForeground(list, selected).let {
      mnemonic.foreground = if (selected) it else JBUI.CurrentTheme.ActionsList.MNEMONIC_FOREGROUND
      main.foreground = it
      extra.foreground = it
    }
    mnemonic.text = value.mnemonic ?: ""
    mnemonic.isVisible = value.mnemonic != null
    value.prepareMainRenderer(main, selected)
    value.prepareExtraRenderer(extra, selected)
    applySpeedSearchHighlighting(switcher, main, false, selected)
    panel.accessibleContext.accessibleName = value.mainText
    panel.accessibleContext.accessibleDescription = value.statusText
    return panel
  }

  private val toolWindowsAllowed = when (switcher.recent) {
    true -> Registry.`is`("ide.recent.files.tool.window.list")
    else -> Registry.`is`("ide.switcher.tool.window.list")
  }

  val toolWindows: List<SwitcherToolWindow> = if (toolWindowsAllowed) {
    val manager = ToolWindowManager.getInstance(switcher.project)
    val windows = manager.toolWindowIds
      .mapNotNull { manager.getToolWindow(it) }
      .filter { it.isAvailable && it.isShowStripeButton }
      .map { SwitcherToolWindow(it, switcher.pinned) }
      .sortedWith(mainTextComparator)

    // TODO: assign mnemonics

    windows
  }
  else emptyList()
}
