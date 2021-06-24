// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.experimental.toolbar.ExperimentalToolbarSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionToolbar.NOWRAP_LAYOUT_POLICY
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.util.ui.JBSwingUtilities
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class NewToolbarRootPaneExtension(val myProject: Project) : IdeRootPaneNorthExtension(), @NotNull Disposable {
  val logger = Logger.getInstance(NewToolbarRootPaneExtension::class.java)
  var inited = false

  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"
    const val navBarKey = "ide.new.navbar"
  }

  private val myPanelWrapper = JPanel(BorderLayout())
  private val myPanel: JPanel = object : JPanel(
    NewToolbarBorderLayout()){
    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder()
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      clearAndRefill()
    }
  }

  init {
    Disposer.register(myProject, this)
    Registry.get(navBarKey).addListener(registryListener, this)
  }

  private fun addGroupComponent(panel: JPanel, layoutConstrains: String , vararg children: AnAction) {
    for (c in children) {
      val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.RUN_TOOLBAR,
                                                                        if (c is ActionGroup) c else DefaultActionGroup(c),
                                                                        true) as ActionToolbarImpl
      toolbar.targetComponent = panel
      toolbar.layoutPolicy = NOWRAP_LAYOUT_POLICY
      panel.add(toolbar, layoutConstrains)
    }
  }

  override fun getKey(): String {
    return NEW_TOOLBAR_KEY
  }

  private fun clearAndRefill(){
    myPanelWrapper.removeAll()
    myPanel.removeAll()

    fillToolbar()
  }

  private fun fillToolbar(){
    val toolbarSettingsService = ToolbarSettings.Companion.getInstance()
    if (toolbarSettingsService is ExperimentalToolbarSettings) {
      val visibleAndEnabled = toolbarSettingsService.showNewToolbar && !instance.presentationMode
      if (visibleAndEnabled) {
        logger.info("ToolbarSettingsService is ExperimentalToolbarSettings")
        logger.info("Show new toolbar: ${toolbarSettingsService.showNewToolbar}, presentation mode: ${instance.presentationMode}")
        logger.info(
          "Show old main toolbar: ${toolbarSettingsService.isToolbarVisible()}, old navbar visible: ${toolbarSettingsService.isNavBarVisible()}")

        myPanelWrapper.add(myPanel, BorderLayout.CENTER)

        val newToolbarActions = CustomActionsSchema.getInstance().getCorrectedAction("NewToolbarActions")

        val listChildren = (newToolbarActions as ActionGroup).getChildren(null)

        addGroupComponent(myPanel, BorderLayout.EAST, listChildren[2])
        addGroupComponent(myPanel, BorderLayout.CENTER, listChildren[1])
        addGroupComponent(myPanel, BorderLayout.WEST, listChildren[0])

        myPanelWrapper.isVisible = true
        myPanelWrapper.isEnabled = true
        logger.info("finish revalidate newtoolbar")
      }
      else {
        myPanel.removeAll()
        myPanelWrapper.removeAll()
      }
    }
    else {
      myPanelWrapper.isVisible = false
      myPanelWrapper.isEnabled = false
    }
  }

  override fun revalidate() {
  }

  override fun getComponent(): JComponent {
    return myPanelWrapper
  }

  override fun uiSettingsChanged(settings: UISettings) {
    clearAndRefill()
  }

  override fun copy(): IdeRootPaneNorthExtension {
    return NewToolbarRootPaneExtension(myProject)
  }

  override fun dispose() {
  }

}