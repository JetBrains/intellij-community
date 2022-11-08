// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit

import com.intellij.diagnostic.IdeMessagePanel
import com.intellij.ide.lightEdit.menuBar.LightEditMainMenuHelper
import com.intellij.ide.lightEdit.statusBar.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.createNewProjectFrame
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.LightEditFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.ProjectFrameBounds.Companion.getInstance
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.ui.PopupHandler
import java.awt.Component
import java.awt.Dimension
import java.util.function.BooleanSupplier
import javax.swing.JFrame

internal class LightEditFrameWrapper(
  private val project: Project,
  frame: IdeFrameImpl,
  private val closeHandler: BooleanSupplier
) : ProjectFrameHelper(frame = frame, selfie = null), Disposable, LightEditFrame {
  private var editPanel: LightEditPanel? = null
  private var frameTitleUpdateEnabled = true

  companion object {
    fun allocate(project: Project, frameInfo: FrameInfo?, closeHandler: BooleanSupplier): LightEditFrameWrapper {
      return (WindowManager.getInstance() as WindowManagerImpl).allocateLightEditFrame(project) {
        LightEditFrameWrapper(project = project, frame = createNewProjectFrame(frameInfo), closeHandler = closeHandler)
      } as LightEditFrameWrapper
    }
  }

  override fun getProject(): Project = project

  val lightEditPanel: LightEditPanel
    get() = editPanel!!

  override fun createIdeRootPane(): IdeRootPane {
    return LightEditRootPane(frame = requireNotNullFrame(), frameHelper = this, parentDisposable = this)
  }

  override fun installDefaultProjectStatusBarWidgets(project: Project) {
    val editorManager = LightEditService.getInstance().editorManager
    val statusBar = statusBar!!
    statusBar.addWidgetToLeft(LightEditModeNotificationWidget(), this)
    statusBar.addWidget(LightEditPositionWidget(project, editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this)
    statusBar.addWidget(LightEditAutosaveWidget(editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this)
    statusBar.addWidget(LightEditEncodingWidgetWrapper(project), StatusBar.Anchors.after(StatusBar.StandardWidgets.POSITION_PANEL), this)
    statusBar.addWidget(LightEditLineSeparatorWidgetWrapper(project), StatusBar.Anchors.before(LightEditEncodingWidgetWrapper.WIDGET_ID),
                        this)
    PopupHandler.installPopupMenu(statusBar, StatusBarWidgetsActionGroup.GROUP_ID, ActionPlaces.STATUS_BAR_PLACE)
    val statusBarWidgetManager = project.service<StatusBarWidgetsManager>()
    ApplicationManager.getApplication().invokeLater { statusBarWidgetManager.installPendingWidgets() }
    Disposer.register(statusBar) { statusBarWidgetManager.disableAllWidgets() }
  }

  override val titleInfoProviders: List<TitleInfoProvider>
    get() = emptyList()

  override fun createCloseProjectWindowHelper(): CloseProjectWindowHelper {
    return object : CloseProjectWindowHelper() {
      override fun windowClosing(project: Project?) {
        if (closeHandler.asBoolean) {
          super.windowClosing(project)
        }
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(editPanel!!)
  }

  fun closeAndDispose(lightEditServiceImpl: LightEditServiceImpl) {
    val frame = requireNotNullFrame()
    val frameInfo = getInstance(project).getActualFrameInfoInDeviceSpace(
      frameHelper = this,
      frame = frame,
      windowManager = (WindowManager.getInstance() as WindowManagerImpl)
    )

    lightEditServiceImpl.setFrameInfo(frameInfo)
    frame.isVisible = false
    Disposer.dispose(this)
  }

  private inner class LightEditRootPane(frame: JFrame,
                                        frameHelper: IdeFrame,
                                        parentDisposable: Disposable) : IdeRootPane(frame, frameHelper, parentDisposable) {
    override fun createCenterComponent(frame: JFrame, parentDisposable: Disposable): Component {
      val panel = LightEditPanel(LightEditUtil.requireProject())
      editPanel = panel
      return panel
    }

    override fun getToolWindowPane(): ToolWindowPane {
      throw IllegalStateException("Tool windows are unavailable in LightEdit")
    }

    override val mainMenuActionGroup: ActionGroup
      get() = LightEditMainMenuHelper().mainMenuActionGroup

    override fun createStatusBar(frame: IdeFrame): IdeStatusBarImpl {
      return object : IdeStatusBarImpl(frame, false) {
        override fun updateUI() {
          setUI(LightEditStatusBarUI())
        }

        override fun getPreferredSize(): Dimension = LightEditStatusBarUI.withHeight(super.getPreferredSize())
      }
    }

    override fun updateNorthComponents() {}
    override fun installNorthComponents(project: Project) {}
    override fun deinstallNorthComponents(project: Project) {}
  }

  fun setFrameTitleUpdateEnabled(frameTitleUpdateEnabled: Boolean) {
    this.frameTitleUpdateEnabled = frameTitleUpdateEnabled
  }

  override fun setFrameTitle(text: String) {
    if (frameTitleUpdateEnabled) {
      super.setFrameTitle(text)
    }
  }
}