// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.experimental.toolbar.ExperimentalToolbarSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.util.containers.stream
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.NotNull
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class NewToolbarRootPaneExtension(val myProject: Project) : IdeRootPaneNorthExtension(), @NotNull Disposable {
  private val myPresentationFactory = PresentationFactory()

  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"
    const val navBarKey = "ide.new.navbar"
  }

  private val myPanel: JPanel = object : JPanel(MigLayout("fillx, ins 0 5 0 5", "[][grow][]")) {
    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder()
      //a hack to have the panel full width when the navbar is added TODO find why it is not so by default
      addComponentListener(object: ComponentAdapter(){
        override fun componentMoved(e: ComponentEvent?) {
          setLocation(0,0)
          if(parent != null) {
            setSize(parent.width, height)
          }
        }
      })
    }
    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  private val myLeftPanel: JPanel = JPanel(MigLayout("ins 0, fillx"))
  private val myCenterPanel: JPanel = JPanel(MigLayout("ins 0, fillx"))
  private val myRightPanel: JPanel = JPanel(MigLayout("ins 0, fillx"))


  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      revalidate()
    }
  }

  init {
    Registry.get(navBarKey).addListener(registryListener, this)
    myPanel.add(myLeftPanel, "growx, align leading")
    myPanel.add(myCenterPanel, "growx, align trailing, width max")
    myPanel.add(myRightPanel, "growx, align trailing, width pref")

    val newToolbarActions = ActionManager.getInstance().getAction("NewToolbarActions")

    val listChildren = (newToolbarActions as ActionGroup).getChildren(null)
    addGroupComponent<LeftToolbarGroup>(listChildren, myLeftPanel, "align leading, width pref!")
    addGroupComponent<CenterToolbarGroup>(listChildren, myCenterPanel, "align trailing, width pref!")
    addGroupComponent<RightToolbarGroup>(listChildren, myRightPanel, "align trailing, width pref!")

    revalidate()
    myProject.messageBus.connect().subscribe(UISettingsListener.TOPIC, UISettingsListener { revalidate() })

  }

  private inline fun <reified T : DefaultActionGroup> addGroupComponent(listChildren: Array<AnAction>,
                                                                        panel: JPanel,
                                                                        layoutConstrains: String) {
    val ag = listChildren.stream().filter { it is T }.findAny()
    if (ag.isPresent) {
      val actionsGroup = ag.get() as @NotNull ActionGroup
      val children = actionsGroup.getChildren(null)
      for (c in children) {
        when (c) {
          is CustomComponentAction -> {
            val component = c.createCustomComponent(myPresentationFactory.getPresentation(c), ActionPlaces.NEW_TOOLBAR)
            panel.add(component, layoutConstrains)
          }
          is Separator -> {
            panel.add(SeparatorComponent(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
                                         SeparatorOrientation.VERTICAL),
                      "$layoutConstrains, height 80%!")
          }
          is AnAction -> {
            val actionButton = ActionButton(c, myPresentationFactory.getPresentation(c), ActionPlaces.NEW_TOOLBAR,
                                            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
            panel.add(actionButton, layoutConstrains)
          }
          else -> {
            panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.NEW_TOOLBAR,
                                                                      DefaultActionGroup(c), true).component, layoutConstrains)
          }
        }
      }
    }
  }

  override fun getKey(): String {
    return NEW_TOOLBAR_KEY
  }

  override fun revalidate() {
    val toolbarSettingsService = ToolbarSettings.Companion.getInstance()
    if (toolbarSettingsService is ExperimentalToolbarSettings) {
      myPanel.isVisible = toolbarSettingsService.showNewToolbar && !instance.presentationMode
      myPanel.isEnabled = myPanel.isVisible
      myLeftPanel.isVisible = myPanel.isVisible
      myRightPanel.isVisible = myPanel.isVisible
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