// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.core.CoreBundle
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType.*
import com.intellij.openapi.externalSystem.autoimport.MockProjectAware.RefreshCollisionPassType
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ModificationType
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy
import com.intellij.openapi.externalSystem.service.project.autoimport.ProjectAware
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.platform.externalSystem.testFramework.TestExternalSystemManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import org.jetbrains.concurrency.AsyncPromise
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class AutoImportTestCase : ExternalSystemTestCase() {
  override fun getTestsTempDir() = "tmp${System.currentTimeMillis()}"

  override fun getExternalSystemConfigFileName() = throw UnsupportedOperationException()

  protected lateinit var testDisposable: Disposable
  private val notificationAware get() =  AutoImportProjectNotificationAware.getInstance(myProject)
  private val projectTracker get() = AutoImportProjectTracker.getInstance(myProject).also { it.enableAutoImportInTests() }
  private val projectTrackerSettings get() = AutoImportProjectTrackerSettings.getInstance(myProject)

  private fun ensureExistsParentDirectory(relativePath: String): VirtualFile {
    return relativePath.split("/").dropLast(1)
      .fold(myProjectRoot!!) { file, name -> file.findOrCreateChildDirectory(name) }
  }

  private fun VirtualFile.findOrCreateChildDirectory(name: String): VirtualFile {
    val file = findChild(name) ?: createChildDirectory(null, name)
    if (!file.isDirectory) throw IOException("Cannot create directory $name")
    return file
  }

  private fun VirtualFile.findOrCreateChildFile(name: String): VirtualFile {
    val file = findChild(name) ?: createChildData(null, name)
    if (file.isDirectory) throw IOException("Cannot create file $name")
    return file
  }

  protected fun createVirtualFile(relativePath: String) = runWriteAction {
    val directory = ensureExistsParentDirectory(relativePath)
    directory.createChildData(null, relativePath.split("/").last())
  }

  protected fun findOrCreateVirtualFile(relativePath: String) = runWriteAction {
    val directory = ensureExistsParentDirectory(relativePath)
    directory.findOrCreateChildFile(relativePath.split("/").last())
  }

  protected fun createIoFile(relativePath: String): VirtualFile {
    val file = getFile(relativePath)
    refreshIoFiles(file.path)
    FileUtil.ensureExists(file.parentFile)
    FileUtil.ensureCanCreateFile(file)
    if (!file.createNewFile()) {
      throw IOException(CoreBundle.message("file.create.already.exists.error", parentPath, relativePath))
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
  }

  protected fun findOrCreateDirectory(relativePath: String) = createProjectSubDir(relativePath)!!

  private fun <R> runWriteAction(update: () -> R): R =
    WriteCommandAction.runWriteCommandAction(myProject, Computable { update() })

  protected fun pathsOf(vararg files: VirtualFile): Set<String> {
    return files.mapTo(LinkedHashSet()) { it.path }
  }

  private fun getPath(relativePath: String) = "$projectPath/$relativePath"

  private fun getFile(relativePath: String) = File(getPath(relativePath))

  private fun VirtualFile.updateIoFile(action: File.() -> Unit) {
    File(path).apply(action)
    refreshIoFiles(path)
  }

  private fun refreshIoFiles(vararg paths: String) {
    val localFileSystem = LocalFileSystem.getInstance()
    localFileSystem.refreshIoFiles(paths.map { File(it) }, false, true, null)
  }

  protected fun VirtualFile.appendLineInIoFile(line: String) =
    appendStringInIoFile(line + "\n")

  protected fun VirtualFile.appendStringInIoFile(string: String) =
    updateIoFile { appendText(string) }

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

  protected fun VirtualFile.move(newParentRelativePath: String) =
    move(findOrCreateDirectory(newParentRelativePath))

  protected fun VirtualFile.move(parent: VirtualFile) =
    runWriteAction { move(null, parent) }

  protected fun VirtualFile.removeContent() =
    runWriteAction { getOutputStream(null).close() }

  protected fun VirtualFile.replaceContent(content: ByteArray) =
    runWriteAction {
      getOutputStream(null).use { stream ->
        stream.write(content)
      }
    }

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

  protected fun VirtualFile.appendLine(line: String) =
    appendString(line + "\n")

  protected fun VirtualFile.appendString(string: String) =
    runWriteAction { VfsUtil.saveText(this, VfsUtil.loadText(this) + string) }

  protected fun VirtualFile.replaceString(old: String, new: String) =
    runWriteAction { VfsUtil.saveText(this, VfsUtil.loadText(this).replaceFirst(old, new)) }

  protected fun VirtualFile.delete() =
    runWriteAction { delete(null) }

  fun VirtualFile.modify(modificationType: ModificationType = ModificationType.INTERNAL) {
    when (modificationType) {
      ModificationType.INTERNAL -> appendLine("println 'hello'")
      ModificationType.EXTERNAL -> appendLineInIoFile("println 'hello'")
    }
  }

  protected fun VirtualFile.revert() = replaceString("println 'hello'\n", "")

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

  protected fun Document.appendLine(line: String) =
    appendString(line + "\n")

  protected fun Document.appendString(string: String) =
    runWriteAction { setText(text + string) }

  fun Document.modify() {
    appendLine("println 'hello'")
  }

  protected fun registerSettingsFile(projectAware: MockProjectAware, relativePath: String) {
    projectAware.registerSettingsFile(getPath(relativePath))
  }

  protected fun register(projectAware: ExternalSystemProjectAware, activate: Boolean = true, parentDisposable: Disposable? = null) {
    if (parentDisposable != null) {
      projectTracker.register(projectAware, parentDisposable)
    }
    else {
      projectTracker.register(projectAware)
    }
    if (activate) activate(projectAware.projectId)
  }

  protected fun activate(projectId: ExternalSystemProjectId) {
    projectTracker.activate(projectId)
  }

  protected fun remove(projectId: ExternalSystemProjectId) = projectTracker.remove(projectId)

  protected fun scheduleProjectReload() = projectTracker.scheduleProjectRefresh()

  protected fun markDirty(projectId: ExternalSystemProjectId) = projectTracker.markDirty(projectId)

  protected fun enableAsyncExecution() {
    projectTracker.isAsyncChangesProcessing = true
  }

  @Suppress("SameParameterValue")
  protected fun setDispatcherMergingSpan(delay: Int) {
    projectTracker.setDispatcherMergingSpan(delay)
  }

  protected fun setAutoReloadType(type: AutoReloadType) {
    projectTrackerSettings.autoReloadType = type
  }

  protected fun initialize() = projectTracker.initializeComponent()

  protected fun getState() = projectTracker.state to projectTrackerSettings.state

  private fun loadState(state: Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State>) {
    projectTracker.loadState(state.first)
    projectTrackerSettings.loadState(state.second)
  }

  protected fun assertProjectAware(projectAware: MockProjectAware,
                                   refresh: Int? = null,
                                   settingsAccess: Int? = null,
                                   subscribe: Int? = null,
                                   unsubscribe: Int? = null,
                                   event: String) {
    if (refresh != null) assertCountEvent(refresh, projectAware.refreshCounter.get(), "project refresh", event)
    if (settingsAccess != null) assertCountEvent(settingsAccess, projectAware.settingsAccessCounter.get(), "access to settings", event)
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

  protected fun assertProjectTrackerSettings(autoReloadType: AutoReloadType, event: String) {
    val message = when (autoReloadType) {
      ALL -> "Auto reload must be enabled"
      SELECTIVE -> "Auto reload must be enabled"
      NONE -> "Auto reload must be disabled"
    }
    assertEquals("$message on $event", autoReloadType, projectTrackerSettings.autoReloadType)
  }

  protected fun assertActivationStatus(vararg projects: ExternalSystemProjectId, event: String) {
    val message = when (projects.isEmpty()) {
      true -> "Auto reload must be activated"
      false -> "Auto reload must be deactivated"
    }
    assertEquals("$message on $event", projects.toSet(), projectTracker.getActivatedProjects())
  }

  protected fun assertNotificationAware(vararg projects: ExternalSystemProjectId, event: String) {
    val message = when (projects.isEmpty()) {
      true -> "Notification must be expired"
      else -> "Notification must be notified"
    }
    assertEquals("$message on $event", projects.toSet(), notificationAware.getProjectsWithNotification())
  }

  protected fun modification(action: () -> Unit) {
    BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeStarted(myProject, "modification")
    action()
    BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeCompleted(myProject)
  }

  private fun <S : Any, R> ComponentManager.replaceService(aClass: Class<S>, service: S, action: () -> R): R {
    Disposer.newDisposable().use {
      replaceService(aClass, service, it)
      return action()
    }
  }

  override fun setUp() {
    super.setUp()
    testDisposable = Disposer.newDisposable()
    myProject.replaceService(ExternalSystemProjectTrackerSettings::class.java, AutoImportProjectTrackerSettings(), testDisposable)
    myProject.replaceService(ExternalSystemProjectTracker::class.java, AutoImportProjectTracker(myProject), testDisposable)
  }

  override fun tearDown() {
    Disposer.dispose(testDisposable)
    super.tearDown()
  }

  protected fun simpleModificationTest(test: SimpleModificationTestBench.() -> Unit) {
    simpleTest("settings.groovy", "") {
      assertState(
        refresh = 1,
        settingsAccess = 1,
        notified = false,
        subscribe = 2,
        unsubscribe = 0,
        autoReloadType = SELECTIVE,
        event = "register project without cache"
      )
      resetAssertionCounters()
      SimpleModificationTestBench(projectAware, it).test()
    }
  }

  protected fun simpleTest(
    fileRelativePath: String,
    content: String? = null,
    state: Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> =
      AutoImportProjectTracker.State() to AutoImportProjectTrackerSettings.State(),
    test: SimpleTestBench.(VirtualFile) -> Unit
  ): Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> {
    return projectTrackerTest(state) {
      val projectId = ExternalSystemProjectId(TEST_EXTERNAL_SYSTEM_ID, projectPath)
      val projectAware = mockProjectAware(projectId)
      val file = findOrCreateVirtualFile(fileRelativePath)
      content?.let { file.replaceContent(it) }
      projectAware.registerSettingsFile(file.path)
      register(projectAware, parentDisposable = it)
      SimpleTestBench(projectAware).test(file)
    }
  }

  protected fun simpleTest(
    state: Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> =
      AutoImportProjectTracker.State() to AutoImportProjectTrackerSettings.State(),
    test: SimpleTestBench.() -> Unit
  ): Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> {
    return projectTrackerTest(state) {
      val projectId = ExternalSystemProjectId(TEST_EXTERNAL_SYSTEM_ID, projectPath)
      val projectAware = mockProjectAware(projectId)
      register(projectAware, parentDisposable = it)
      SimpleTestBench(projectAware).test()
    }
  }

  protected fun testWithDummyExternalSystem(
    fileRelativePath: String,
    content: String? = null,
    autoImportAwareCondition: Ref<Boolean>? = null,
    state: Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> =
      AutoImportProjectTracker.State() to AutoImportProjectTrackerSettings.State(),
    test: DummyExternalSystemTestBench.(VirtualFile) -> Unit
  ): Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> {
    val externalSystemManagers = ExternalSystemManager.EP_NAME.extensionList + TestExternalSystemManager(myProject)
    ExtensionTestUtil.maskExtensions(ExternalSystemManager.EP_NAME, externalSystemManagers, testRootDisposable)
    return projectTrackerTest(state) {
      val projectId = ExternalSystemProjectId(TEST_EXTERNAL_SYSTEM_ID, projectPath)
      val autoImportAware = object : ExternalSystemAutoImportAware {
        override fun getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String {
          return fileRelativePath
        }

        override fun isApplicable(resolverPolicy: ProjectResolverPolicy?): Boolean {
          return autoImportAwareCondition == null || autoImportAwareCondition.get()
        }
      }
      val file = findOrCreateVirtualFile(fileRelativePath)
      content?.let { file.replaceContent(it) }
      val projectAware = ProjectAwareWrapper(ProjectAware(myProject, projectId, autoImportAware), it)
      register(projectAware, parentDisposable = it)
      DummyExternalSystemTestBench(projectAware).test(file)
    }
  }

  private fun projectTrackerTest(
    state: Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> =
      AutoImportProjectTracker.State() to AutoImportProjectTrackerSettings.State(),
    test: (Disposable) -> Unit
  ): Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> {
    return myProject.replaceService(ExternalSystemProjectTrackerSettings::class.java, AutoImportProjectTrackerSettings()) {
      myProject.replaceService(ExternalSystemProjectTracker::class.java, AutoImportProjectTracker(myProject)) {
        loadState(state)
        initialize()
        Disposer.newDisposable().use {
          test(it)
          getState()
        }
      }
    }
  }

  protected open inner class SimpleTestBench(val projectAware: MockProjectAware) {

    fun markDirty() = markDirty(projectAware.projectId)

    fun forceRefreshProject() = projectAware.forceReloadProject()

    fun registerProjectAware() = register(projectAware)

    fun removeProjectAware() = remove(projectAware.projectId)

    fun registerSettingsFile(file: VirtualFile) = projectAware.registerSettingsFile(file.path)

    fun registerSettingsFile(relativePath: String) = projectAware.registerSettingsFile(getPath(relativePath))

    fun onceDuringRefresh(action: (ExternalSystemProjectReloadContext) -> Unit) = projectAware.onceDuringRefresh(action)
    fun duringRefresh(times: Int, action: (ExternalSystemProjectReloadContext) -> Unit) = projectAware.duringRefresh(times, action)
    fun duringRefresh(action: (ExternalSystemProjectReloadContext) -> Unit, parentDisposable: Disposable) =
      projectAware.duringRefresh(action, parentDisposable)

    fun onceAfterRefresh(action: (ExternalSystemRefreshStatus) -> Unit) = projectAware.onceAfterRefresh(action)
    fun afterRefresh(times: Int, action: (ExternalSystemRefreshStatus) -> Unit) = projectAware.afterRefresh(times, action)
    fun afterRefresh(action: (ExternalSystemRefreshStatus) -> Unit, parentDisposable: Disposable) =
      projectAware.afterRefresh(action, parentDisposable)

    fun onceBeforeRefresh(action: () -> Unit) = projectAware.onceBeforeRefresh(action)
    fun beforeRefresh(times: Int, action: () -> Unit) = projectAware.beforeRefresh(times, action)
    fun beforeRefresh(action: () -> Unit, parentDisposable: Disposable) =
      projectAware.beforeRefresh(action, parentDisposable)

    fun setRefreshStatus(status: ExternalSystemRefreshStatus) = projectAware.refreshStatus.set(status)

    fun setRefreshCollisionPassType(type: RefreshCollisionPassType) = projectAware.refreshCollisionPassType.set(type)

    fun resetAssertionCounters() = projectAware.resetAssertionCounters()

    fun createSettingsVirtualFile(relativePath: String): VirtualFile {
      registerSettingsFile(relativePath)
      return findOrCreateVirtualFile(relativePath)
    }

    fun withLinkedProject(fileRelativePath: String, test: SimpleTestBench.(VirtualFile) -> Unit) {
      val projectId = ExternalSystemProjectId(projectAware.projectId.systemId, "$projectPath/$name")
      val projectAware = mockProjectAware(projectId)
      Disposer.newDisposable().use {
        val file = findOrCreateVirtualFile("$name/$fileRelativePath")
        projectAware.registerSettingsFile(file.path)
        register(projectAware, parentDisposable = it)
        SimpleTestBench(projectAware).test(file)
      }
    }

    fun assertState(refresh: Int? = null,
                    settingsAccess: Int? = null,
                    subscribe: Int? = null,
                    unsubscribe: Int? = null,
                    autoReloadType: AutoReloadType = SELECTIVE,
                    notified: Boolean,
                    event: String) {
      assertProjectAware(projectAware, refresh, settingsAccess, subscribe, unsubscribe, event)
      assertProjectTrackerSettings(autoReloadType, event = event)
      when (notified) {
        true -> assertNotificationAware(projectAware.projectId, event = event)
        else -> assertNotificationAware(event = event)
      }
    }

    fun waitForProjectRefresh(expectedRefreshes: Int = 1, action: () -> Unit) {
      require(expectedRefreshes > 0)
      Disposer.newDisposable(testDisposable, "waitForProjectRefresh").use { parentDisposable ->
        val promise = AsyncPromise<ExternalSystemRefreshStatus>()
        val uncompletedRefreshes = AtomicInteger(expectedRefreshes)
        afterRefresh({ status ->
          if (uncompletedRefreshes.decrementAndGet() == 0) {
            promise.setResult(status)
          }
        }, parentDisposable)
        action()
        invokeAndWaitIfNeeded {
          PlatformTestUtil.waitForPromise(promise, TimeUnit.SECONDS.toMillis(10))
        }
      }
    }
  }

  fun mockProjectAware(projectId: ExternalSystemProjectId) = MockProjectAware(projectId, myProject, testDisposable)

  protected inner class SimpleModificationTestBench(
    projectAware: MockProjectAware,
    private val settingsFile: VirtualFile
  ) : SimpleTestBench(projectAware) {
    fun modifySettingsFile(modificationType: ModificationType = ModificationType.INTERNAL) = settingsFile.modify(modificationType)
  }

  inner class DummyExternalSystemTestBench(val projectAware: ProjectAwareWrapper) {
    fun assertState(refresh: Int? = null,
                    beforeRefresh: Int? = null,
                    afterRefresh: Int? = null,
                    subscribe: Int? = null,
                    unsubscribe: Int? = null,
                    autoReloadType: AutoReloadType = SELECTIVE,
                    event: String) {
      assertProjectAware(projectAware, refresh, beforeRefresh, afterRefresh, subscribe, unsubscribe, event)
      assertProjectTrackerSettings(autoReloadType, event = event)
    }

    private fun assertProjectAware(projectAware: ProjectAwareWrapper,
                                   refresh: Int? = null,
                                   beforeRefresh: Int? = null,
                                   afterRefresh: Int? = null,
                                   subscribe: Int? = null,
                                   unsubscribe: Int? = null,
                                   event: String) {
      if (refresh != null) assertCountEvent(refresh, projectAware.refreshCounter.get(), "project refresh", event)
      if (beforeRefresh != null) assertCountEvent(beforeRefresh, projectAware.beforeRefreshCounter.get(), "project before refresh", event)
      if (afterRefresh != null) assertCountEvent(afterRefresh, projectAware.afterRefreshCounter.get(), "project after refresh", event)
      if (subscribe != null) assertCountEvent(subscribe, projectAware.subscribeCounter.get(), "subscribe", event)
      if (unsubscribe != null) assertCountEvent(unsubscribe, projectAware.unsubscribeCounter.get(), "unsubscribe", event)
    }
  }
}