// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.test.ExternalSystemTestCase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsBundle
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import java.io.File
import java.io.IOException

abstract class AutoImportTestCase : ExternalSystemTestCase() {
  override fun getTestsTempDir() = "tmp${System.currentTimeMillis()}"

  override fun getExternalSystemConfigFileName() = throw UnsupportedOperationException()

  private lateinit var testDisposable: Disposable
  private val notificationAware get() = ProjectNotificationAware.getInstance(myProject)
  private val projectTracker get() = AutoImportProjectTracker.getInstance(myProject).also { it.enableAutoImportInTests() }

  private fun doRecursive(relativePath: String, stepInto: VirtualFile.(String) -> VirtualFile) = runWriteAction {
    var file = myProjectRoot!!
    for (part in relativePath.split("/")) {
      file = file.stepInto(part)
    }
    file
  }

  protected fun createVirtualFile(relativePath: String) =
    doRecursive(relativePath) { createChildData(null, it) }

  private fun findOrCreateVirtualFile(relativePath: String) =
    doRecursive(relativePath) { findOrCreateChildData(null, it) }


  protected fun createIoFile(relativePath: String): VirtualFile {
    val file = File(projectPath, relativePath)
    FileUtil.ensureExists(file.parentFile)
    FileUtil.ensureCanCreateFile(file)
    if (!file.createNewFile()) {
      throw IOException(VfsBundle.message("file.create.already.exists.error", parentPath, relativePath))
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
  }

  protected fun findOrCreateDirectory(relativePath: String) = createProjectSubDir(relativePath)!!

  private fun <R> runWriteAction(update: () -> R): R =
    WriteCommandAction.runWriteCommandAction(myProject, Computable { update() })

  private fun getPath(relativePath: String) = "$projectPath/$relativePath"

  private fun getFile(relativePath: String) = File(projectPath, relativePath)

  private fun VirtualFile.updateIoFile(action: File.() -> Unit) {
    File(path).apply(action)
    refreshIoFiles(path)
  }

  private fun refreshIoFiles(vararg paths: String) {
    val localFileSystem = LocalFileSystem.getInstance()
    localFileSystem.refreshIoFiles(paths.map { File(it) }, false, true, null)
  }

  protected fun VirtualFile.replaceContentInIoFile(content: String) =
    updateIoFile { writeText(content) }

  protected fun VirtualFile.replaceStringInIoFile(old: String, new: String) =
    updateIoFile { writeText(readText().replace(old, new)) }

  protected fun VirtualFile.deleteIoFile() =
    updateIoFile { delete() }

  protected fun VirtualFile.rename(name: String) =
    runWriteAction { rename(null, name) }

  protected fun VirtualFile.copy(relativePath: String) =
    runWriteAction {
      val newFile = getFile(relativePath)
      val parent = VfsUtil.findFileByIoFile(newFile.parentFile, true)!!
      copy(null, parent, newFile.name)
    }

  protected fun VirtualFile.move(parent: VirtualFile) =
    runWriteAction { move(null, parent) }

  protected fun VirtualFile.removeContent() =
    runWriteAction { VfsUtil.saveText(this, "") }

  protected fun VirtualFile.replaceContent(content: String) =
    runWriteAction { VfsUtil.saveText(this, content) }

  protected fun VirtualFile.insertString(offset: Int, string: String) =
    runWriteAction {
      val text = VfsUtil.loadText(this)
      val before = text.substring(0, offset)
      val after = text.substring(offset, text.length)
      VfsUtil.saveText(this, before + string + after)
    }

  protected fun VirtualFile.insertStringAfter(prefix: String, string: String) =
    runWriteAction {
      val text = VfsUtil.loadText(this)
      val offset = text.indexOf(prefix)
      val before = text.substring(0, offset)
      val after = text.substring(offset + prefix.length, text.length)
      VfsUtil.saveText(this, before + prefix + string + after)
    }

  protected fun VirtualFile.appendString(string: String) =
    runWriteAction { VfsUtil.saveText(this, VfsUtil.loadText(this) + string) }

  protected fun VirtualFile.replaceString(old: String, new: String) =
    runWriteAction { VfsUtil.saveText(this, VfsUtil.loadText(this).replace(old, new)) }

  protected fun VirtualFile.delete() =
    runWriteAction { delete(null) }

  protected fun VirtualFile.asDocument(): Document {
    val fileDocumentManager = FileDocumentManager.getInstance()
    return fileDocumentManager.getDocument(this)!!
  }

  protected fun Document.save() =
    runWriteAction { FileDocumentManager.getInstance().saveDocument(this) }

  protected fun Document.replaceContent(content: String) =
    runWriteAction { replaceString(0, text.length, content) }

  protected fun Document.replaceString(old: String, new: String) =
    runWriteAction {
      val startOffset = text.indexOf(old)
      val endOffset = startOffset + old.length
      replaceString(startOffset, endOffset, new)
    }

  protected fun register(projectAware: ExternalSystemProjectAware) = projectTracker.register(projectAware)

  protected fun remove(projectId: ExternalSystemProjectId) = projectTracker.remove(projectId)

  protected fun refreshProject() = projectTracker.scheduleProjectRefresh()

  protected fun forceRefreshProject(projectId: ExternalSystemProjectId) {
    projectTracker.markDirty(projectId)
    projectTracker.scheduleProjectRefresh()
  }

  private fun loadState(state: AutoImportProjectTracker.State) = projectTracker.loadState(state)

  protected fun initialize() = projectTracker.initializeComponent()

  protected fun getState() = projectTracker.state

  protected fun assertProjectAware(projectAware: MockProjectAware,
                                   refresh: Int? = null,
                                   subscribe: Int? = null,
                                   unsubscribe: Int? = null,
                                   event: String) {
    if (refresh != null) assertCountEvent(refresh, projectAware.refreshCounter.get(), "project refresh", event)
    if (subscribe != null) assertCountEvent(subscribe, projectAware.subscribeCounter.get(), "subscribe", event)
    if (unsubscribe != null) assertCountEvent(unsubscribe, projectAware.unsubscribeCounter.get(), "unsubscribe", event)
  }

  private fun assertCountEvent(expected: Int, actual: Int, countEvent: String, event: String) {
    val message = when {
      actual > expected -> "Unexpected $countEvent event"
      actual < expected -> "Expected $countEvent event"
      else -> ""
    }
    assertEquals("$message on $event", expected, actual)
  }


  protected fun assertNotificationAware(vararg projects: ExternalSystemProjectId, event: String) {
    val message = when (projects.isEmpty()) {
      true -> "Notification must be expired"
      else -> "Notification must be notified for $projects"
    }
    assertEquals("$message on $event", projects.toSet(), notificationAware.getProjectsWithNotification())
  }

  protected fun modification(action: () -> Unit) {
    BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeStarted(myProject, "modification")
    action()
    BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeCompleted(myProject)
  }

  private fun <S : Any, R> ComponentManager.replaceService(aClass: Class<S>, service: S, action: () -> R): R {
    val temporaryDisposable = Disposer.newDisposable()
    try {
      replaceService(aClass, service, temporaryDisposable)
      return action()
    }
    finally {
      Disposer.dispose(temporaryDisposable)
    }
  }

  override fun setUp() {
    super.setUp()
    testDisposable = Disposer.newDisposable()
    myProject.replaceService(ExternalSystemProjectTracker::class.java, AutoImportProjectTracker(myProject), testDisposable)
  }

  override fun tearDown() {
    Disposer.dispose(testDisposable)
    super.tearDown()
  }

  protected fun simpleTest(fileRelativePath: String,
                           content: String? = null,
                           state: AutoImportProjectTracker.State = AutoImportProjectTracker.State(),
                           test: SimpleTestBench.(VirtualFile) -> Unit): AutoImportProjectTracker.State {
    return myProject.replaceService(ExternalSystemProjectTracker::class.java, AutoImportProjectTracker(myProject)) {
      val systemId = ProjectSystemId("External System")
      val projectId = ExternalSystemProjectId(systemId, projectPath)
      val projectAware = MockProjectAware(projectId)
      loadState(state)
      initialize()
      val file = findOrCreateVirtualFile(fileRelativePath)
      content?.let { file.replaceContent(it) }
      projectAware.settingsFiles.add(file.path)
      register(projectAware)
      SimpleTestBench(projectAware).test(file)
      val newState = getState()
      remove(projectAware.projectId)
      newState
    }
  }

  protected inner class SimpleTestBench(private val projectAware: MockProjectAware) {

    fun registerProjectAware() = register(projectAware)

    fun removeProjectAware() = remove(projectAware.projectId)

    fun registerSettingsFile(file: VirtualFile) = projectAware.settingsFiles.add(file.path)

    fun registerSettingsFile(relativePath: String) = projectAware.settingsFiles.add(getPath(relativePath))

    fun setRefreshStatus(status: ExternalSystemRefreshStatus) {
      projectAware.refreshStatus = status
    }

    fun assertState(refresh: Int? = null,
                    subscribe: Int? = null,
                    unsubscribe: Int? = null,
                    notified: Boolean,
                    event: String) {
      assertProjectAware(projectAware, refresh, subscribe, unsubscribe, event)
      when (notified) {
        true -> assertNotificationAware(projectAware.projectId, event = event)
        else -> assertNotificationAware(event = event)
      }
    }
  }
}