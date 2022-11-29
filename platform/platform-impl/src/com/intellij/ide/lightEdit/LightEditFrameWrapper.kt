// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit

import com.intellij.diagnostic.IdeMessagePanel
import com.intellij.ide.lightEdit.menuBar.LightEditMainMenuHelper
import com.intellij.ide.lightEdit.statusBar.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.project.impl.createNewProjectFrame
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs
import com.intellij.openapi.wm.impl.ProjectFrameBounds.Companion.getInstance
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.ui.PopupHandler
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Dimension
import java.util.function.BooleanSupplier
import javax.swing.JFrame

internal class LightEditFrameWrapper(
  private val project: Project,
  frame: IdeFrameImpl,
  private val closeHandler: BooleanSupplier
) : ProjectFrameHelper(frame = frame), Disposable, LightEditFrame {
  private var editPanel: LightEditPanel? = null
  private var frameTitleUpdateEnabled = true

  companion object {
    @RequiresEdt
    fun allocate(project: Project, frameInfo: FrameInfo?, closeHandler: BooleanSupplier): LightEditFrameWrapper {
      return runBlockingModal(project, "") {
        withContext(Dispatchers.EDT) {
          allocateLightEditFrame(project) { frame ->
            LightEditFrameWrapper(project = project, frame = frame ?: createNewProjectFrame(frameInfo).create(), closeHandler = closeHandler)
          } as LightEditFrameWrapper
        }
      }
    }

    /**
     * This method is not used in a normal conditions. Only for light edit.
     */
    private suspend fun allocateLightEditFrame(project: Project,
                                               projectFrameHelperFactory: (IdeFrameImpl?) -> ProjectFrameHelper): ProjectFrameHelper {
      val windowManager = WindowManager.getInstance() as WindowManagerImpl
      windowManager.getFrameHelper(project)?.let {
        return it
      }

      windowManager.removeAndGetRootFrame()?.let { frame ->
        val frameHelper = projectFrameHelperFactory(frame)
        windowManager.lightFrameAssign(project, frameHelper)
        return frameHelper
      }

      val frame = projectFrameHelperFactory(null)
      frame.init()
      var frameInfo: FrameInfo? = null
      val lastFocusedProjectFrame = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project?.let(windowManager::getFrameHelper)
      if (lastFocusedProjectFrame != null) {
        frameInfo = getFrameInfoByFrameHelper(lastFocusedProjectFrame)
        if (frameInfo.bounds == null) {
          frameInfo = windowManager.defaultFrameInfoHelper.info
        }
      }

      if (frameInfo?.bounds != null) {
        // update default frame info - newly opened project frame should be the same as last opened
        if (frameInfo !== windowManager.defaultFrameInfoHelper.info) {
          windowManager.defaultFrameInfoHelper.copyFrom(frameInfo)
        }
        frameInfo.bounds?.let {
          frame.frame.bounds = FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(it).first
        }
      }

      windowManager.lightFrameAssign(project, frame)
      val uiFrame = frame.frame
      if (frameInfo != null) {
        uiFrame.extendedState = frameInfo.extendedState
      }
      uiFrame.isVisible = true
      if (isFullScreenSupportedInCurrentOs() && frameInfo != null && frameInfo.fullScreen) {
        frame.toggleFullScreen(true)
      }
      uiFrame.addComponentListener(FrameStateListener(windowManager.defaultFrameInfoHelper, frame))
      IdeMenuBar.installAppMenuIfNeeded(uiFrame)

      @Suppress("DEPRECATION")
      project.coroutineScope.launch {
        ProjectManagerImpl.dispatchEarlyNotifications()
      }
      return frame
    }
  }

  override fun getProject(): Project = project

  val lightEditPanel: LightEditPanel
    get() = editPanel!!

  override fun createIdeRootPane(loadingState: FrameLoadingState?): IdeRootPane = LightEditRootPane(frame = frame, parentDisposable = this)

  override suspend fun installDefaultProjectStatusBarWidgets(project: Project) {
    val editorManager = LightEditService.getInstance().editorManager
    val statusBar = statusBar!!
    statusBar.addWidgetToLeft(LightEditModeNotificationWidget(), this)
    statusBar.addWidget(LightEditPositionWidget(project, editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this)
    statusBar.addWidget(LightEditAutosaveWidget(editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this)
    statusBar.addWidget(LightEditEncodingWidgetWrapper(project), StatusBar.Anchors.after(StatusBar.StandardWidgets.POSITION_PANEL), this)
    statusBar.addWidget(widget = LightEditLineSeparatorWidgetWrapper(project),
                        anchor = StatusBar.Anchors.before(LightEditEncodingWidgetWrapper.WIDGET_ID),
                        parentDisposable = this)
    PopupHandler.installPopupMenu(statusBar, StatusBarWidgetsActionGroup.GROUP_ID, ActionPlaces.STATUS_BAR_PLACE)
    project.service<StatusBarWidgetsManager>().init { statusBar }
  }

  override fun getTitleInfoProviders(): List<TitleInfoProvider> = emptyList()

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
                                        parentDisposable: Disposable) : IdeRootPane(frame = frame,
                                                                                    parentDisposable = parentDisposable,
                                                                                    loadingState = null) {
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

    override fun createStatusBar(frameHelper: ProjectFrameHelper): IdeStatusBarImpl {
      return object : IdeStatusBarImpl(frameHelper = frameHelper, addToolWindowsWidget = false) {
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