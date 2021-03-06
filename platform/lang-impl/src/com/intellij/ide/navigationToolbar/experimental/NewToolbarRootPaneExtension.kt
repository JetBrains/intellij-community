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
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class NewToolbarRootPaneExtension(val myProject: Project) : IdeRootPaneNorthExtension(), @NotNull Disposable {
  val logger = Logger.getInstance(NewToolbarRootPaneExtension::class.java)

  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"
    const val navBarKey = "ide.new.navbar"
  }

  private val myPanelWrapper = JPanel(BorderLayout())
  private val myPanel: JPanel = object : JPanel(
    MigLayout("fillx,novisualpadding,ins 0 ${JBUI.scale(5)} 0 ${JBUI.scale(2)},righttoleft", "[shrink 1]0[shrink 2]0:push[shrink 0]")) {
    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder()
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }
  private val myRightPanel: JPanel = JPanel(MigLayout("ins 0, gap 0, fillx, novisualpadding"))
  private val myCenterPanel: JPanel = JPanel(MigLayout("ins 0, gap 0, fillx, novisualpadding"))
  private val myLeftPanel: JPanel = JPanel(MigLayout("ins 0, gap 0, fillx, novisualpadding"))

  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      revalidate()
    }
  }

  init {
    Registry.get(navBarKey).addListener(registryListener, this)
    revalidate()
    myProject.messageBus.connect().subscribe(UISettingsListener.TOPIC, UISettingsListener { revalidate() })

  }

  private fun addGroupComponent(panel: JPanel, layoutConstrains: String, vararg children: AnAction) {
    for (c in children) {

          val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.NEW_TOOLBAR,
                                                                        if (c is ActionGroup) c else DefaultActionGroup(c),
                                                                        true) as ActionToolbarImpl
          toolbar.layoutPolicy = NOWRAP_LAYOUT_POLICY
          toolbar.addNotify()
          panel.add(toolbar, if (c is CustomComponentAction) "$layoutConstrains, shrink 0" else layoutConstrains)

    }
  }

  override fun getKey(): String {
    return NEW_TOOLBAR_KEY
  }

  override fun revalidate() {
    myPanelWrapper.removeAll()
    myPanel.removeAll()
    myRightPanel.removeAll()
    myCenterPanel.removeAll()
    myLeftPanel.removeAll()

    val toolbarSettingsService = ToolbarSettings.Companion.getInstance()
    if (toolbarSettingsService is ExperimentalToolbarSettings) {
      logger.info("ToolbarSettingsService is ExperimentalToolbarSettings")
      logger.info("Show new toolbar: ${toolbarSettingsService.showNewToolbar}, presentation mode: ${instance.presentationMode}")
      logger.info("Show old main toolbar: ${toolbarSettingsService.isToolbarVisible()}, old navbar visible: ${toolbarSettingsService.isNavBarVisible()}")

      myPanelWrapper.add(myPanel, BorderLayout.CENTER)
      myPanel.add(myRightPanel, "growx, align trailing, width pref, shrink 0")
      myPanel.add(myCenterPanel, "growx, align leading")
      myPanel.add(myLeftPanel, "growx, align leading")

      val newToolbarActions = CustomActionsSchema.getInstance().getCorrectedAction("NewToolbarActions")

      val listChildren = (newToolbarActions as ActionGroup).getChildren(null)
      addGroupComponent(myLeftPanel, "align leading", listChildren[0])
      addGroupComponent(myCenterPanel, "align leading, width 0:pref:max", listChildren[1])
      addGroupComponent(myRightPanel, "align trailing, width pref!", listChildren[2])

      myPanelWrapper.isVisible = toolbarSettingsService.showNewToolbar && !instance.presentationMode
      myPanelWrapper.isEnabled = myPanelWrapper.isVisible
      myPanel.isVisible = myPanelWrapper.isVisible
      myLeftPanel.isVisible = myPanelWrapper.isVisible
      myRightPanel.isVisible = myPanelWrapper.isVisible
      logger.info("finish revalidate newtoolbar")
    }
    else {
      myPanel.isVisible = false
      myLeftPanel.isVisible = false
      myRightPanel.isVisible = false
    }
  }

  override fun getComponent(): JComponent {
    return myPanelWrapper
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