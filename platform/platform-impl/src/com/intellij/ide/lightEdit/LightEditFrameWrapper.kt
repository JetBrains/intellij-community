// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit

import com.apple.eawt.event.FullScreenEvent
import com.intellij.diagnostic.IdeMessagePanel
import com.intellij.ide.lightEdit.menuBar.getLightEditMainMenuActionGroup
import com.intellij.ide.lightEdit.project.LightEditFileEditorManagerImpl
import com.intellij.ide.lightEdit.statusBar.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.project.impl.applyBoundsOrDefault
import com.intellij.openapi.project.impl.createNewProjectFrameProducer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs
import com.intellij.openapi.wm.impl.ProjectFrameBounds.Companion.getInstance
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.status.adaptV2Widget
import com.intellij.platform.ide.menu.installAppMenuIfNeeded
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.mac.MacFullScreenControlsManager
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.ui.mac.MacMainFrameDecorator.FSAdapter
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import javax.swing.JComponent

@RequiresEdt
internal fun allocateLightEditFrame(project: Project, frameInfo: FrameInfo?): LightEditFrameWrapper {
  return runWithModalProgressBlocking(project, "") {
    withContext(Dispatchers.EDT) {
      val wrapper = allocateLightEditFrame(project) { frame ->
        LightEditFrameWrapper(project = project, frame = frame ?: createNewProjectFrameProducer(frameInfo).create())
      } as LightEditFrameWrapper
      (project.serviceAsync<FileEditorManager>() as LightEditFileEditorManagerImpl).internalInit()
      wrapper
    }
  }
}

internal class LightEditFrameWrapper(
  private val project: Project,
  frame: IdeFrameImpl
) : ProjectFrameHelper(frame = frame), Disposable, LightEditFrame {
  private var editPanel: LightEditPanel? = null
  private var frameTitleUpdateEnabled = true

  override val isLightEdit: Boolean = true
  override val mainMenuActionGroup: ActionGroup? = getLightEditMainMenuActionGroup()

  override fun getProject(): Project = project

  val lightEditPanel: LightEditPanel
    get() = editPanel!!

  override fun createCenterComponent(): JComponent {
    val panel = LightEditPanel(LightEditUtil.requireProject())
    editPanel = panel
    return panel
  }

  override fun createStatusBar(): IdeStatusBarImpl {
    return object : IdeStatusBarImpl(parentCs = cs, getProject = { project }, addToolWindowWidget = false) {
      override fun updateUI() {
        setUI(LightEditStatusBarUI())
      }

      override fun getPreferredSize(): Dimension = LightEditStatusBarUI.withHeight(super.getPreferredSize())
    }
  }

  override suspend fun installDefaultProjectStatusBarWidgets(project: Project) {
    val editorManager = LightEditService.getInstance().editorManager
    val statusBar = statusBar!!

    val coroutineScope = (project as ComponentManagerEx).getCoroutineScope()
    statusBar.addWidgetToLeft(LightEditModeNotificationWidget())

    val dataContext = object : WidgetPresentationDataContext {
      override val project: Project
        get() = project

      override val currentFileEditor: StateFlow<FileEditor?> by lazy {
        val flow = MutableStateFlow<FileEditor?>(null)
        editorManager.addListener(object : LightEditorListener {
          override fun afterSelect(editorInfo: LightEditorInfo?) {
            flow.value = editorInfo?.fileEditor
          }
        })
        flow
      }
    }

    statusBar.init(
      project = project,
      frame = frame,
      extraItems = listOf(
        LightEditAutosaveWidget(editorManager) to LoadingOrder.before(IdeMessagePanel.FATAL_ERROR),
        LightEditEncodingWidgetWrapper(project, coroutineScope) to LoadingOrder.after(StatusBar.StandardWidgets.POSITION_PANEL),
        LightEditLineSeparatorWidgetWrapper(project, coroutineScope) to LoadingOrder.before(LightEditEncodingWidgetWrapper.WIDGET_ID),
        adaptV2Widget(StatusBar.StandardWidgets.POSITION_PANEL, dataContext) { scope ->
          LightEditPositionWidget(dataContext = dataContext, scope = scope, editorManager = editorManager)
        } to LoadingOrder.before(IdeMessagePanel.FATAL_ERROR),
      ),
    )
  }

  override fun getTitleInfoProviders(): List<TitleInfoProvider> = emptyList()

  override fun dispose() {
    val frameInfo = getInstance(project).getActualFrameInfoInDeviceSpace(
      frameHelper = this,
      frame = frame,
      windowManager = (WindowManager.getInstance() as WindowManagerImpl)
    )
    val lightEditServiceImpl = LightEditService.getInstance() as LightEditServiceImpl
    lightEditServiceImpl.setFrameInfo(frameInfo)
    lightEditServiceImpl.frameDisposed()
    Disposer.dispose(editPanel!!)
    super.dispose()
  }

  fun setFrameTitleUpdateEnabled(frameTitleUpdateEnabled: Boolean) {
    this.frameTitleUpdateEnabled = frameTitleUpdateEnabled
  }

  override fun setFrameTitle(text: String) {
    if (frameTitleUpdateEnabled) {
      super.setFrameTitle(text)
    }
  }

  override fun getNorthExtension(key: String): JComponent? = null
}

/**
 * This method is not used in a normal conditions. Only for light edit.
 */
private suspend fun allocateLightEditFrame(project: Project,
                                           projectFrameHelperFactory: (IdeFrameImpl?) -> LightEditFrameWrapper): ProjectFrameHelper {
  val windowManager = WindowManager.getInstance() as WindowManagerImpl
  windowManager.getFrameHelper(project)?.let {
    return it
  }

  windowManager.removeAndGetRootFrame()?.let { frame ->
    val frameHelper = projectFrameHelperFactory(frame).apply {
      setupFor(project)
    }
    windowManager.assignFrame(frameHelper, project, false)
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
      applyBoundsOrDefault(frame.frame, FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(it)?.first)
    }
  }

  if (SystemInfoRt.isMac) {
    val decorator = frame.getDecorator()
    if (decorator is MacMainFrameDecorator) {
      decorator.dispatcher.addListener(object : FSAdapter() {
        override fun windowEnteringFullScreen(e: FullScreenEvent?) {
          MacFullScreenControlsManager.configureForLightEdit(true)
        }

        override fun windowExitedFullScreen(e: FullScreenEvent?) {
          MacFullScreenControlsManager.configureForLightEdit(false)
        }
      })
    }
  }

  frame.setupFor(project)
  windowManager.assignFrame(frame, project, false)

  val uiFrame = frame.frame
  if (frameInfo != null) {
    uiFrame.extendedState = frameInfo.extendedState
  }
  uiFrame.isVisible = true
  if (isFullScreenSupportedInCurrentOs() && frameInfo != null && frameInfo.fullScreen) {
    frame.toggleFullScreen(true)
  }
  uiFrame.addComponentListener(FrameStateListener(windowManager.defaultFrameInfoHelper))
  installAppMenuIfNeeded(uiFrame)

  (project as ComponentManagerEx).getCoroutineScope().launch {
    ProjectManagerImpl.dispatchEarlyNotifications()
  }
  return frame
}

private suspend fun LightEditFrameWrapper.setupFor(project: Project) {
  setRawProject(project)
  setProject(project)
  installDefaultProjectStatusBarWidgets(project)
  updateTitle(project)
}

