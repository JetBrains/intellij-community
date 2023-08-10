// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar

import com.intellij.ide.navbar.ide.NavBarService
import com.intellij.ide.navbar.ide.isNavbarShown
import com.intellij.ide.navbar.ide.isNavbarV2Enabled
import com.intellij.ide.navigationToolbar.NavBarRootPaneExtension.NavBarWrapperPanel
import com.intellij.ide.ui.NavBarLocation
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomisedActionGroup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.status.InfoAndProgressPanel.AutoscrollLimit
import com.intellij.openapi.wm.impl.status.InfoAndProgressPanel.ScrollableToSelected
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.ui.hover.HoverListener
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.awt.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

/**
 * @author Konstantin Bulenkov
 */
internal class NavBarRootPaneExtension : IdeRootPaneNorthExtension {
  companion object {
    const val PANEL_KEY: String = "NavBarPanel"
  }

  override fun createComponent(project: Project, isDocked: Boolean): JComponent? {
    val settings = UISettings.getInstance()
    if (!ExperimentalUI.isNewUI() || (settings.showNavigationBar && settings.navBarLocation == NavBarLocation.TOP)) {
      return MyNavBarWrapperPanel(project, useAsComponent = true)
    }
    else {
      return null
    }
  }

  override fun component(project: Project, isDocked: Boolean, statusBar: StatusBar): Flow<JComponent?>? {
    if (!ExperimentalUI.isNewUI()) {
      return null
    }

    // cold flow
    return channelFlow {
      send(createPanelIfApplicable(UISettings.getInstance()))

      project.messageBus.connect(this@channelFlow).subscribe(UISettingsListener.TOPIC, UISettingsListener { uiSettings ->
        trySendBlocking(createPanelIfApplicable(uiSettings))
        if (isNavbarV2Enabled) {
          NavBarService.getInstance(project).uiSettingsChanged(uiSettings)
        }
      })
      awaitClose()
    }
      .distinctUntilChanged()
      .map {
        val uiSettings = UISettings.getInstance()
        val result = it.configure(project, statusBar, uiSettings)
        if (isNavbarV2Enabled) {
          NavBarService.getInstance(project).uiSettingsChanged(uiSettings)
        }
        result
      }
      .buffer(Channel.UNLIMITED)
  }

  private fun createPanelIfApplicable(uiSettings: UISettings): NavBarMode {
    return when {
      !uiSettings.showNavigationBar -> DisabledNavBarMode
      uiSettings.navBarLocation == NavBarLocation.TOP -> TopNavBarMode
      else -> BottomNavBarMode
    }
  }

  override val key: String
    get() = IdeStatusBarImpl.NAVBAR_WIDGET_KEY

  // used externally
  abstract class NavBarWrapperPanel(layout: LayoutManager?) : JPanel(layout), UISettingsListener {
    override fun getComponentGraphics(graphics: Graphics): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }
}

private fun createNavBarPanel(scrollPane: JScrollPane, navigationBar: JComponent): NavBarContainer {
  val navBarPanel = NavBarContainer(layout = BorderLayout(), scrollPane = scrollPane, navigationBar = navigationBar)
  navBarPanel.add(scrollPane, BorderLayout.CENTER)
  navBarPanel.isOpaque = !ExperimentalUI.isNewUI()
  navBarPanel.updateUI()
  if (ExperimentalUI.isNewNavbar()) {
    val hoverListener: HoverListener = object : HoverListener() {
      override fun mouseEntered(component: Component, x: Int, y: Int) {
        toggleScrollBar(true, scrollPane)
      }

      override fun mouseMoved(component: Component, x: Int, y: Int) {}
      override fun mouseExited(component: Component) {
        toggleScrollBar(false, scrollPane)
      }
    }
    hoverListener.addTo(navBarPanel)
  }
  return navBarPanel
}

internal class MyNavBarWrapperPanel(private val project: Project, useAsComponent: Boolean) : NavBarWrapperPanel(BorderLayout()) {
  private var navBarPanel: JComponent? = null
  private var runPanel: JPanel? = null
  private var navToolbarGroupExist: Boolean? = null
  private var navigationBar: JComponent? = null
  var scrollPane: JScrollPane? = null

  init {
    if (useAsComponent) {
      add(getNavBarPanel(), BorderLayout.CENTER)
      revalidate()
      uiSettingsChanged(UISettings.getInstance())
    }
  }

  fun getNavBarPanel(): JComponent {
    navBarPanel?.let {
      return it
    }

    val navigationBar: JComponent
    if (isNavbarV2Enabled) {
      navigationBar = NavBarService.getInstance(project).createNavBarPanel()
    }
    else {
      @Suppress("DEPRECATION")
      navigationBar = ReusableNavBarPanel(project, true)
      @Suppress("DEPRECATION")
      (navigationBar as NavBarPanel).model.setFixedComponent(true)
    }
    this.navigationBar = navigationBar

    putClientProperty(NavBarRootPaneExtension.PANEL_KEY, navigationBar)
    scrollPane = ScrollPaneFactory.createScrollPane(navigationBar)
    updateScrollBarFlippedState(location = null, scrollPane = scrollPane!!)
    val navBarPanel = createNavBarPanel(scrollPane = scrollPane!!, navigationBar = navigationBar)
    navBarPanel.putClientProperty(NavBarRootPaneExtension.PANEL_KEY, navigationBar)
    this.navBarPanel = navBarPanel
    return navBarPanel
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    if (project.isDisposed) {
      return
    }

    toggleRunPanel(isShowToolPanel(uiSettings))
    toggleNavPanel(uiSettings)
    if (isNavbarV2Enabled) {
      NavBarService.getInstance(project).uiSettingsChanged(uiSettings)
    }

    val navigationBar = navigationBar ?: return
    @Suppress("DEPRECATION")
    if (navigationBar is NavBarPanel) {
      navigationBar.updateState(uiSettings.showNavigationBar)
    }

    val visible = uiSettings.isNavbarShown()
    if (ExperimentalUI.isNewUI()) {
      scrollPane!!.isVisible = visible
    }
    navigationBar.revalidate()
    isVisible = visible
    revalidate()
    repaint()
    if (componentCount > 0) {
      val c = getComponent(0)
      if (c is JComponent) {
        c.isOpaque = false
      }
    }
  }

  override fun getInsets(): Insets {
    @Suppress("DEPRECATION")
    return com.intellij.ide.navigationToolbar.ui.NavBarUIManager.getUI().getWrapperPanelInsets(super.getInsets())
  }

  private fun runToolbarExists(): Boolean {
    if (navToolbarGroupExist == null) {
      val correctedAction = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_NAVBAR_TOOLBAR)
      navToolbarGroupExist = correctedAction is DefaultActionGroup && correctedAction.childrenCount > 0 ||
                             correctedAction is CustomisedActionGroup && correctedAction.getFirstAction() != null
    }
    return navToolbarGroupExist!!
  }

  private fun toggleNavPanel(settings: UISettings) {
    val show = if (ExperimentalUI.isNewUI()) {
      settings.showNavigationBar && settings.navBarLocation === NavBarLocation.TOP
    }
    else {
      settings.isNavbarShown()
    }

    if (show) {
      ApplicationManager.getApplication().invokeLater {
        val navBarPanel = getNavBarPanel()
        add(navBarPanel, BorderLayout.CENTER)
        navBarPanel.updateUI()
      }
    }
    else {
      (layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)?.let { remove(it) }
    }

    updateScrollBarFlippedState(settings.navBarLocation, scrollPane!!)
    isVisible = show
  }

  private fun toggleRunPanel(show: Boolean) {
    // don't care about CompletableFuture - not used in a New UI
    CompletableFuture
      .supplyAsync({ CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_NAVBAR_TOOLBAR) },
                   AppExecutorUtil.getAppExecutorService())
      .thenAcceptAsync(
        { action ->
          if (show && runPanel == null && runToolbarExists()) {
            if (runPanel != null) {
              remove(runPanel)
              runPanel = null
            }

            val manager = ActionManager.getInstance()
            if (action is ActionGroup) {
              val actionToolbar = manager.createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR,
                                                              (action as ActionGroup?)!!, true)
              actionToolbar.targetComponent = null
              val runPanel = object : JPanel(BorderLayout()) {
                override fun doLayout() {
                  alignVertically(this)
                }
              }
              this.runPanel = runPanel
              runPanel.isOpaque = false
              runPanel.add(actionToolbar.component, BorderLayout.CENTER)
              val needGap = isNeedGap(action)
              runPanel.border = JBUI.Borders.emptyLeft(if (needGap) 5 else 1)
              NavBarLeftSideExtension.EP_NAME.forEachExtensionSafe { extension ->
                extension.process(this, project)
              }
              add(runPanel, BorderLayout.EAST)
            }
          }
          else if (!show && runPanel != null) {
            remove(runPanel)
            runPanel = null
          }
        },
        { command ->
          ApplicationManager.getApplication().invokeLater(command, project.disposed)
        },
      )
  }
}

private fun updateScrollBarFlippedState(location: NavBarLocation?, scrollPane: JScrollPane) {
  if (ExperimentalUI.isNewNavbar()) {
    val effectiveLocation = location ?: UISettings.getInstance().navBarLocation
    val flipState = if (effectiveLocation === NavBarLocation.BOTTOM) JBScrollPane.Flip.VERTICAL else JBScrollPane.Flip.NONE
    scrollPane.putClientProperty(JBScrollPane.Flip::class.java, flipState)
  }
}

private fun toggleScrollBar(isOn: Boolean, scrollPane: JScrollPane) {
  val scrollBar = scrollPane.horizontalScrollBar
  if (scrollBar is JBScrollBar) {
    scrollBar.toggle(isOn)
  }
}

private fun isShowToolPanel(uiSettings: UISettings): Boolean {
  // Evanescent me: fix run panel show condition in ExpUI if necessary.
  if (!ExperimentalUI.isNewUI() && !uiSettings.showMainToolbar && uiSettings.isNavbarShown()) {
    val toolbarSettings = ToolbarSettings.getInstance()
    return !toolbarSettings.isVisible || !toolbarSettings.isAvailable
  }
  return false
}

private fun alignVertically(container: Container) {
  if (container.componentCount == 1) {
    val c = container.getComponent(0)
    val insets = container.insets
    val d = c.preferredSize
    val r = container.bounds
    c.setBounds(insets.left, (r.height - d.height - insets.top - insets.bottom) / 2 + insets.top, r.width - insets.left - insets.right,
                d.height)
  }
}

private fun isNeedGap(group: AnAction): Boolean {
  return getFirstAction(group) is ComboBoxAction
}

private fun getFirstAction(group: AnAction): AnAction? {
  if (group is CustomisedActionGroup) {
    return group.getFirstAction()
  }
  else if (group !is DefaultActionGroup) {
    return null
  }

  var firstAction: AnAction? = null
  for (action in group.getChildActionsOrStubs()) {
    if (action is DefaultActionGroup) {
      firstAction = getFirstAction(action)
    }
    else if (action is Separator || action is ActionGroup) {
      continue
    }
    else {
      firstAction = action
      break
    }

    if (firstAction != null) {
      break
    }
  }
  return firstAction
}

private class NavBarContainer(layout: LayoutManager,
                              private val scrollPane: JScrollPane,
                              private val navigationBar: JComponent?) : JPanel(layout), ScrollableToSelected {
  init {
    updateUI()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val r = scrollPane.bounds
    val g2d = g.create() as Graphics2D
    g2d.translate(r.x, r.y)
    g2d.dispose()
  }

  override fun doLayout() {
    // align vertically
    val r = bounds
    val insets = insets
    val x = insets.left

    @Suppress("UNNECESSARY_SAFE_CALL")
    val scrollPane = scrollPane?.takeIf { it.isVisible } ?: return
    var navBarHeight = scrollPane.preferredSize.height
    if (ExperimentalUI.isNewNavbar()) {
      navBarHeight = r.height
    }
    scrollPane.setBounds(x, (r.height - navBarHeight) / 2, r.width - insets.left - insets.right, navBarHeight)
  }

  override fun updateUI() {
    // updateUI is called from JPanel constructor
    @Suppress("SENSELESS_COMPARISON")
    if (scrollPane == null) {
      return
    }

    super.updateUI()

    val settings = UISettings.getInstance()
    val border = if (!ExperimentalUI.isNewUI() || settings.showNavigationBar) NavBarBorder() else JBUI.Borders.empty()
    if (ExperimentalUI.isNewNavbar()) {
      scrollPane.horizontalScrollBar = JBThinOverlappingScrollBar(Adjustable.HORIZONTAL)
      if (scrollPane is JBScrollPane) {
        scrollPane.setOverlappingScrollBar(true)
      }
      scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
      toggleScrollBar(isOn = false, scrollPane = scrollPane)
    }
    else {
      scrollPane.horizontalScrollBar = null
    }
    scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
    scrollPane.border = border
    scrollPane.isOpaque = false
    scrollPane.viewport.isOpaque = false
    scrollPane.viewportBorder = null
    if (ExperimentalUI.isNewUI()) {
      ClientProperty.put(scrollPane, JBScrollPane.FORCE_HORIZONTAL_SCROLL, true)
      val visible = settings.isNavbarShown()
      scrollPane.isVisible = visible
      @Suppress("DEPRECATION")
      (navigationBar as? NavBarPanel)?.updateState(visible)
    }
    navigationBar?.border = null
  }

  override fun updateAutoscrollLimit(limit: AutoscrollLimit) {
    val navigationBar = navigationBar
    @Suppress("DEPRECATION")
    if (navigationBar is NavBarPanel) {
      navigationBar.updateAutoscrollLimit(limit)
    }
  }
}

private fun setStatusBarCentralWidget(statusBar: StatusBar, component: JComponent?) {
  (statusBar as? IdeStatusBarImpl)?.setCentralWidget(IdeStatusBarImpl.NAVBAR_WIDGET_KEY, component)
}

private sealed interface NavBarMode {
  suspend fun configure(project: Project, statusBar: StatusBar, uiSettings: UISettings): MyNavBarWrapperPanel?
}

private object DisabledNavBarMode : NavBarMode {
  override suspend fun configure(project: Project, statusBar: StatusBar, uiSettings: UISettings): MyNavBarWrapperPanel? {
    withContext(Dispatchers.EDT) {
      setStatusBarCentralWidget(statusBar, null)
    }
    return null
  }
}

private object TopNavBarMode : NavBarMode {
  override suspend fun configure(project: Project, statusBar: StatusBar, uiSettings: UISettings): MyNavBarWrapperPanel {
    return withContext(Dispatchers.EDT) {
      setStatusBarCentralWidget(statusBar, null)
      MyNavBarWrapperPanel(project, useAsComponent = true)
    }
  }
}

private object BottomNavBarMode : NavBarMode {
  override suspend fun configure(project: Project, statusBar: StatusBar, uiSettings: UISettings): MyNavBarWrapperPanel? {
    withContext(Dispatchers.EDT) {
      setStatusBarCentralWidget(statusBar, MyNavBarWrapperPanel(project, useAsComponent = false).getNavBarPanel())
    }
    return null
  }
}