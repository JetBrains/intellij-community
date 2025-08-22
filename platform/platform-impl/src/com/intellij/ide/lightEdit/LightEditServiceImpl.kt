// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.lightEdit.intentions.openInProject.LightEditOpenInProjectIntention
import com.intellij.ide.lightEdit.project.LightEditProjectImpl
import com.intellij.ide.lightEdit.project.LightEditProjectManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx.Companion.getInstanceEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame.Companion.getInstance
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.disposeOnCompletion
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.math.max
import kotlin.system.exitProcess

@ApiStatus.Internal
@State(name = "LightEdit", storages = [Storage(value = "lightEdit.xml", roamingType = RoamingType.DISABLED)])
class LightEditServiceImpl(private val coroutineScope: CoroutineScope)
  : LightEditService, Disposable, LightEditorListener, PersistentStateComponent<LightEditConfiguration> {
  private var frameWrapper: LightEditFrameWrapper? = null
  override val editorManager: LightEditorManagerImpl = LightEditorManagerImpl(this)
  private var configuration = LightEditConfiguration()
  private val lightEditProjectManager = LightEditProjectManager()
  private var editorWindowClosing = false
  private var saveSession = false

  override fun getState(): LightEditConfiguration = configuration

  override fun loadState(state: LightEditConfiguration) {
    configuration = state
  }

  init {
    editorManager.addListener(this)
    val connection = ApplicationManager.getApplication().getMessageBus().connect(coroutineScope)
    connection.subscribe<AppLifecycleListener>(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appClosing() {
        (serviceIfCreated<EncodingManager>() as? EncodingManagerImpl)?.clearDocumentQueue()
        if (frameWrapper != null) {
          closeAndDisposeFrame()
        }
        Disposer.dispose(editorManager)
      }
    })

    editorManager.disposeOnCompletion(coroutineScope)
  }

  private fun init(restoreSession: Boolean) {
    val project = getOrCreateProject()
    invokeOnEdt {
      var notify = false
      if (frameWrapper == null) {
        saveSession = restoreSession
        frameWrapper = allocateLightEditFrame(project, configuration.frameInfo)
        LOG.info("Frame created")
        if (restoreSession) {
          restoreSession()
        }
        notify = true
      }

      val frame = frameWrapper!!.frame
      if (!frame.isVisible) {
        frame.setVisible(true)
        LOG.info("Window opened")
        notify = true
      }

      frameWrapper!!.setFrameTitle(appName)
      if (notify) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(LightEditServiceListener.TOPIC).lightEditWindowOpened(project)
      }
    }
  }

  override fun showEditorWindow() {
    doShowEditorWindow(true)
  }

  private fun doShowEditorWindow(restoreSession: Boolean) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      init(restoreSession)
    }
  }

  override val project: Project?
    get() = lightEditProjectManager.project

  fun getOrCreateProject(): Project = lightEditProjectManager.getOrCreateProject()

  override fun openFile(file: VirtualFile): Project {
    val project = lightEditProjectManager.getOrCreateProject()
    val commandLineOptions = LightEditUtil.getCommandLineOptions()
    doWhenActionManagerInitialized {
      doOpenFile(file, commandLineOptions == null || !commandLineOptions.shouldWait())
    }
    return project
  }

  private fun doOpenFile(file: VirtualFile, restoreSession: Boolean) {
    doShowEditorWindow(restoreSession)
    val openEditorInfo = editorManager.findOpen(file)
    if (openEditorInfo == null) {
      val newEditorInfo = editorManager.createEditor(file)
      if (newEditorInfo != null) {
        addEditorTab(newEditorInfo)
        LOG.info("Opened new tab for ${file.presentableUrl}")
        if (file.getUserData(LightEditUtil.SUGGEST_SWITCH_TO_PROJECT) == true) {
          file.putUserData(LightEditUtil.SUGGEST_SWITCH_TO_PROJECT, null)
          if (LightEditConfiguration.PreferredMode.LightEdit != configuration.preferredMode) {
            suggestSwitchToProject(getOrCreateProject(), file)
          }
        }
      }
      else {
        processNotOpenedFile(file)
      }
    }
    else {
      selectEditorTab(openEditorInfo)
      LOG.info("Selected tab for ${file.presentableUrl}")
    }

    logStartupTime()
  }

  private fun suggestSwitchToProject(project: Project, file: VirtualFile) {
    val dialog = LightEditConfirmationDialog(project)
    dialog.show()
    if (dialog.isDontAsk) {
      when (dialog.exitCode) {
        LightEditConfirmationDialog.STAY_IN_LIGHT_EDIT -> configuration.preferredMode = LightEditConfiguration.PreferredMode.LightEdit
        LightEditConfirmationDialog.PROCEED_TO_PROJECT -> configuration.preferredMode = LightEditConfiguration.PreferredMode.Project
      }
    }
    if (dialog.exitCode == LightEditConfirmationDialog.PROCEED_TO_PROJECT) {
      LightEditOpenInProjectIntention.performOn(getOrCreateProject(), file)
    }
  }

  private fun processNotOpenedFile(file: VirtualFile) {
    val fileType = file.fileType
    val project = lightEditProjectManager.project!!
    Messages.showWarningDialog(project,
                               ApplicationBundle.message("light.edit.unableToOpenFile.text", file.presentableName),
                               ApplicationBundle.message("light.edit.unableToOpenFile.title"))
    LOG.info("Failed to open ${file.presentableUrl}, binary: ${fileType.isBinary()}")
  }

  private fun logStartupTime() {
    if (!ApplicationManager.getApplication().isUnitTestMode() && frameWrapper != null) {
      val info = editPanel.tabs.getSelectedInfo() ?: return
      @Suppress("UsagesOfObsoleteApi")
      UiNotifyConnector.doWhenFirstShown(info.component) {
        coroutineScope.launch(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.STRICT)) {
          LOG.info("Startup took: ${ManagementFactory.getRuntimeMXBean().uptime} ms")
        }
      }
    }
  }

  private fun selectEditorTab(openEditorInfo: LightEditorInfo) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      editPanel.tabs.selectTab(openEditorInfo)
    }
  }

  private fun addEditorTab(newEditorInfo: LightEditorInfo) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      editPanel.tabs.addEditorTab(newEditorInfo)
    }
  }

  fun closeEditor(editorInfo: LightEditorInfo) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      editPanel.tabs.closeTab(editorInfo)
    }
  }

  override fun createNewDocument(preferredSavePath: Path?): LightEditorInfo {
    showEditorWindow()
    val preferredName = preferredSavePath?.fileName?.toString()
    val newEditorInfo = editorManager.createEmptyEditor(preferredName)
    newEditorInfo.setPreferredSavePath(preferredSavePath)
    addEditorTab(newEditorInfo)
    return newEditorInfo
  }

  override fun closeEditorWindow(): Boolean {
    if (!canClose()) {
      LOG.info("Close canceled")
      return false
    }

    val project = frameWrapper!!.getProject()
    frameWrapper!!.frame.setVisible(false)
    saveSession()
    editorWindowClosing = true
    try {
      editorManager.closeAllEditors()
    }
    finally {
      editorWindowClosing = false
    }

    LOG.info("Window closed")
    ApplicationManager.getApplication().getMessageBus().syncPublisher(LightEditServiceListener.TOPIC).lightEditWindowClosed(project)
    if (ProjectManager.getInstance().getOpenProjects().size == 0 && getInstance() == null) {
      closeAndDisposeFrame()
      LOG.info("No open projects or welcome frame, exiting")
      try {
        Disposer.dispose(editorManager)
        ApplicationManager.getApplication().exit()
      }
      catch (e: Throwable) {
        LOG.error(e)
        exitProcess(1)
      }
    }
    else {
      WindowManagerEx.getInstanceEx().releaseFrame(frameWrapper!!)
      frameWrapper = null
    }
    return false
  }

  private fun canClose(): Boolean {
    val documentManager = FileDocumentManager.getInstance()
    return !editorManager.containsUnsavedDocuments() ||
           autoSaveDocuments() ||
           LightEditUtil.confirmClose(
             ApplicationBundle.message("light.edit.exit.message"),
             ApplicationBundle.message("light.edit.exit.title"),
             object : LightEditSaveConfirmationHandler {
               override fun onSave() {
                 documentManager.saveAllDocuments()
               }

               override fun onDiscard() {
                 for (editorInfo in editorManager.unsavedEditors) {
                   val file = editorInfo.getFile()
                   val document = documentManager.getDocument(file)
                   if (document != null) {
                     documentManager.reloadFromDisk(document)
                   }
                 }
               }
             }
           )
  }

  private fun autoSaveDocuments(): Boolean {
    if (isAutosaveMode) {
      FileDocumentManager.getInstance().saveAllDocuments()
      return true
    }
    return false
  }

  @Suppress("DEPRECATION")
  val editPanel: LightEditPanel
    get() {
      assert(!Disposer.isDisposed(frameWrapper!!.lightEditPanel))
      return frameWrapper!!.lightEditPanel
    }

  override fun getSelectedFile(): VirtualFile? {
    val frameWrapper = frameWrapper ?: return null
    val panel = frameWrapper.lightEditPanel
    @Suppress("DEPRECATION")
    if (!Disposer.isDisposed(panel)) {
      return panel.tabs.getSelectedFile()
    }
    return null
  }

  override fun getSelectedFileEditor(): FileEditor? {
    val frameWrapper = frameWrapper ?: return null
    val panel = frameWrapper.lightEditPanel
    @Suppress("DEPRECATION")
    if (!Disposer.isDisposed(panel)) {
      return panel.tabs.getSelectedFileEditor()
    }
    return null
  }

  override fun updateFileStatus(files: Collection<VirtualFile>) {
    val editors = files.mapNotNull { editorManager.findOpen(it) }
    if (!editors.isEmpty()) {
      editorManager.fireFileStatusChanged(editors)
    }
  }

  override fun dispose() {
    if (frameWrapper != null) {
      closeAndDisposeFrame()
    }
  }

  private fun closeAndDisposeFrame() {
    if (frameWrapper != null) {
      Disposer.dispose(frameWrapper!!)
      LOG.info("Frame disposed")
    }
  }

  fun frameDisposed() {
    frameWrapper = null
  }

  override fun afterSelect(editorInfo: LightEditorInfo?) {
    frameWrapper?.setFrameTitle(if (editorInfo == null) appName else getFileTitle(editorInfo))
  }

  override fun afterClose(editorInfo: LightEditorInfo) {
    if (editorManager.editorCount == 0 && !editorWindowClosing) {
      closeEditorWindow()
    }
  }

  private fun saveEditorAs(editorInfo: LightEditorInfo, targetFile: VirtualFile) {
    val newInfo = editorManager.saveAs(editorInfo, targetFile)
    this.editPanel.tabs.replaceTab(editorInfo, newInfo)
  }

  override fun saveToAnotherFile(file: VirtualFile) {
    val editorInfo = editorManager.getEditorInfo(file) ?: return
    val targetFile = LightEditUtil.chooseTargetFile(frameWrapper!!.lightEditPanel, editorInfo)
    if (targetFile != null) {
      saveEditorAs(editorInfo, targetFile)
    }
  }

  override var isAutosaveMode: Boolean
    get() = configuration.autosaveMode
    set(value) {
      configuration.autosaveMode = value
      editorManager.fireAutosaveModeChanged(value)
    }

  @TestOnly
  fun disposeCurrentSession() {
    editorManager.releaseEditors()
    val project = lightEditProjectManager.project
    if (project != null) {
      getInstanceEx().forceCloseProject(project)
    }
  }

  private fun saveSession() {
    if (saveSession) {
      val tabs = frameWrapper!!.lightEditPanel.tabs
      val openFiles = tabs.openFiles
      configuration.sessionFiles = openFiles.map { VfsUtilCore.pathToUrl(it.getPath()) }
    }
  }

  private fun restoreSession() {
    doWhenActionManagerInitialized {
      frameWrapper!!.setFrameTitleUpdateEnabled(false)
      for (path in configuration.sessionFiles) {
        VirtualFileManager.getInstance().findFileByUrl(path)?.let {
          doOpenFile(file = it, restoreSession = false)
        }
      }
      frameWrapper!!.setFrameTitleUpdateEnabled(true)
    }
  }

  fun setFrameInfo(frameInfo: FrameInfo) {
    configuration.frameInfo = frameInfo
  }

  override fun saveNewDocuments() {
    for (virtualFile in editorManager.openFiles) {
      val editorInfo = editorManager.getEditorInfo(virtualFile)!!
      if (editorInfo.isNew()) {
        val preferredTarget = LightEditUtil.getPreferredSaveTarget(editorInfo)
        if (preferredTarget == null) {
          saveToAnotherFile(virtualFile)
        }
        else {
          saveEditorAs(editorInfo, preferredTarget)
        }
      }
    }
  }

  override fun isTabNavigationAvailable(navigationAction: AnAction): Boolean {
    return this.editPanel.tabs.isTabNavigationAvailable(navigationAction)
  }

  override fun navigateToTab(navigationAction: AnAction) {
    this.editPanel.tabs.navigateToTab(navigationAction)
  }

  override val isPreferProjectMode: Boolean
    get() = configuration.preferredMode != null && LightEditConfiguration.PreferredMode.Project == configuration.preferredMode

  override fun isLightEditEnabled(): Boolean {
    return LightEditUtil.isLightEditEnabled()
  }

  override fun isLightEditProject(project: Project): Boolean {
    return project is LightEditProjectImpl
  }

  override fun openFile(path: Path, suggestSwitchToProject: Boolean): Project? {
    return LightEditUtil.openFile(path, suggestSwitchToProject)
  }

  override fun isForceOpenInLightEditMode(): Boolean {
    return LightEditUtil.isForceOpenInLightEditMode()
  }
}

private val LOG = logger<LightEditServiceImpl>()

private val appName: String
  get() = ApplicationInfo.getInstance().getVersionName()

private fun doWhenActionManagerInitialized(callback: () -> Unit) {
  val created = serviceIfCreated<ActionManager>()
  if (created == null) {
    NonUrgentExecutor.getInstance().execute(Runnable {
      ActionManager.getInstance()
      invokeOnEdt(callback)
    })
  }
  else {
    invokeOnEdt(callback)
  }
}

private fun invokeOnEdt(callback: Runnable) {
  if (ApplicationManager.getApplication().isDispatchThread()) {
    callback.run()
  }
  else {
    ApplicationManager.getApplication().invokeLater(callback)
  }
}

private fun getFileTitle(editorInfo: LightEditorInfo): String {
  val file = editorInfo.getFile()
  val titleBuilder = StringBuilder()
  titleBuilder.append(file.presentableName)
  val parentPath: String? = getPresentablePath(editorInfo)
  if (parentPath != null) {
    titleBuilder.append(" - ").append(truncateUrl(parentPath))
  }
  return titleBuilder.toString()
}

private fun getPresentablePath(editorInfo: LightEditorInfo): String? {
  val file = editorInfo.getFile()
  if (file is LightVirtualFile) {
    val preferredPath = editorInfo.getPreferredSavePath()
    preferredPath?.parent?.let {
      return it.toString()
    }
  }
  else {
    file.getParent()?.let {
      return it.presentableUrl
    }
  }
  return null
}

private fun truncateUrl(url: String): String {
  val slashPos = max(url.lastIndexOf('\\'), url.lastIndexOf('/'))
  if (slashPos >= 0) {
    val withoutLast = url.take(slashPos)
    val prevSlashPos = max(withoutLast.lastIndexOf('\\'), withoutLast.lastIndexOf('/'))
    if (prevSlashPos >= 0) {
      val truncated = url.substring(prevSlashPos)
      if (url != truncated) {
        return "..." + url.substring(prevSlashPos)
      }
    }
  }
  return url
}
