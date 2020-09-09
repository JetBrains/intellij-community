// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.experimental.toolbar.ExperimentalToolbarSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.util.containers.stream
import com.intellij.util.ui.JBSwingUtilities
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JPanel

class NewToolbarRootPaneExtension(val myProject: Project) : IdeRootPaneNorthExtension(), @NotNull Disposable {

  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"
    const val navBarKey = "ide.new.navbar"
    const val runDebugKey = "ide.new.navbar.run.debug"
    const val vcsKey = "ide.new.navbar.vcs.group"
  }

  private val myPanel: JPanel = object : JPanel() {
    override fun getComponentGraphics(graphics: Graphics?): Graphics? {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  private val myLeftPanel: JPanel = JPanel(BorderLayout())
  private val myCenterPanel: JPanel = JPanel(BorderLayout())
  private val myRightPanel: JPanel = JPanel(BorderLayout())


  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      revalidate()
    }
  }

  init {
    val manager = ActionManager.getInstance()

    Registry.get(navBarKey).addListener(registryListener, this)
    Registry.get(runDebugKey).addListener(registryListener, this)
    Registry.get(vcsKey).addListener(registryListener, this)

    myPanel.layout = BorderLayout()
    myPanel.add(myLeftPanel, BorderLayout.WEST)
    myPanel.add(myCenterPanel, BorderLayout.CENTER)
    myPanel.add(myRightPanel, BorderLayout.EAST)
    myPanel.isOpaque = false
    myLeftPanel.isOpaque = false
    myRightPanel.isOpaque = false

    val newToolbarActions = ActionManager.getInstance().getAction("NewToolbarActions")

    val listChildren = (newToolbarActions as ActionGroup).getChildren(null)
    addGroupComponent<LeftToolbarGroup>(listChildren, myLeftPanel, BorderLayout.EAST)
    addGroupComponent<CenterToolbarGroup>(listChildren, myCenterPanel, BorderLayout.EAST)
    addGroupComponent<RightToolbarGroup>(listChildren, myRightPanel, BorderLayout.WEST)

    revalidate()
    myProject.messageBus.connect().subscribe(UISettingsListener.TOPIC, UISettingsListener { revalidate() })

  }

  private inline fun <reified T : DefaultActionGroup> addGroupComponent(listChildren: Array<AnAction>, panel: JPanel, alignment: String) {
    val actionsGroup = listChildren.stream().filter { it is T }.findAny()
    if (actionsGroup.isPresent) {
      val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR,
                                                                          actionsGroup.get() as @NotNull ActionGroup, true)
      val component = actionToolbar.component
      panel.add(component, alignment)
    }
  }

  override fun getKey(): String {
    return NEW_TOOLBAR_KEY
  }

  override fun revalidate() {
    val toolbarSettingsService = ToolbarSettings.Companion.getInstance()
    if (toolbarSettingsService is ExperimentalToolbarSettings) {
      myPanel.isVisible = toolbarSettingsService.showNewToolbar && !instance.presentationMode
      myLeftPanel.isVisible = toolbarSettingsService.showNewNavbarVcsGroup
      myRightPanel.isVisible = toolbarSettingsService.showNewNavbarRunGroup
    }
    else {
      myPanel.isVisible = false
      myLeftPanel.isVisible = false
      myRightPanel.isVisible = false
    }
  }

  override fun getComponent(): JComponent {
    return myPanel
  }

  override fun uiSettingsChanged(settings: UISettings) {
    revalidate()
  }

  override fun copy(): IdeRootPaneNorthExtension {
    return NewToolbarRootPaneExtension(myProject)
  }

  override fun dispose() {
  }

}