// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.INTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType.*
import com.intellij.openapi.externalSystem.autoimport.MockProjectAware.ReloadCollisionPassType
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.*
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.testFramework.refreshVfs
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.testFramework.utils.vfs.getFile
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualFile
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Suppress("unused", "MemberVisibilityCanBePrivate", "SameParameterValue")
abstract class AutoReloadTestCase : ExternalSystemTestCase() {

  override fun runInDispatchThread() = false

  override fun getTestsTempDir() = "tmp${System.currentTimeMillis()}"

  override fun getExternalSystemConfigFileName() = throw UnsupportedOperationException()

  protected lateinit var testDisposable: Disposable
  private val notificationAware get() = AutoImportProjectNotificationAware.getInstance(myProject)
  private val projectTracker get() = AutoImportProjectTracker.getInstance(myProject)
  private val projectTrackerSettings get() = AutoImportProjectTrackerSettings.getInstance(myProject)

  protected val projectRoot: VirtualFile get() = myProjectRoot!!

  protected val projectNioPath: Path get() = projectRoot.toNioPath()

  private fun <R> runWriteAction(update: () -> R): R =
    WriteCommandAction.runWriteCommandAction(myProject, Computable { update() })

  protected fun pathsOf(vararg files: VirtualFile): Set<String> =
    files.mapTo(LinkedHashSet()) { it.path }

  protected fun createFile(relativePath: String): VirtualFile =
    runWriteAction { projectRoot.createFile(relativePath) }

  protected fun findOrCreateFile(relativePath: String): VirtualFile =
    runWriteAction { projectRoot.findOrCreateFile(relativePath) }

  protected fun findOrCreateDirectory(relativePath: String): VirtualFile =
    runWriteAction { projectRoot.findOrCreateDirectory(relativePath) }

  protected fun getFile(relativePath: String): VirtualFile =
    runWriteAction { projectRoot.getFile(relativePath) }

  protected fun createIoFileUnsafe(relativePath: String): Path =
    projectNioPath.createFile(relativePath)

  protected fun createIoFile(relativePath: String): VirtualFile {
    projectNioPath.refreshVfs(relativePath) // ensure that file is removed from VFS
    projectNioPath.createFile(relativePath)
    return projectNioPath.getResolvedPath(relativePath).refreshAndGetVirtualFile()
  }

  private fun VirtualFile.updateIoFile(action: (Path) -> Unit) {
    val path = toNioPath()
    action(path)
    path.refreshVfs() // ensure that file is updated in VFS
  }

  protected fun VirtualFile.appendLineInIoFile(line: String) =
    appendStringInIoFile(line + "\n")

  protected fun VirtualFile.appendStringInIoFile(string: String) =
    updateIoFile { it.appendText(string) }

  protected fun VirtualFile.replaceContentInIoFile(content: String) =
    updateIoFile { it.writeText(content) }

  protected fun VirtualFile.replaceStringInIoFile(old: String, new: String) =
    updateIoFile { it.writeText(it.readText().replace(old, new)) }

  protected fun VirtualFile.deleteIoFile() =
    updateIoFile { it.deleteRecursively() }

  protected fun VirtualFile.rename(name: String) =
    runWriteAction { rename(null, name) }

  protected fun VirtualFile.copy(name: String, parentRelativePath: String = "."): VirtualFile =
    runWriteAction {
      val parent = projectRoot.findOrCreateDirectory(parentRelativePath)
      copy(null, parent, name)
    }

  protected fun VirtualFile.move(parent: VirtualFile) =
    runWriteAction { move(null, parent) }

  protected fun VirtualFile.removeContent() =
    runWriteAction { getOutputStream(null).close() }

  protected fun VirtualFile.replaceContent(content: ByteArray) =
    runWriteAction { writeBytes(content) }

  protected fun VirtualFile.replaceContent(content: String) =
    runWriteAction { writeText(content) }

  protected fun VirtualFile.insertString(offset: Int, string: String) =
    runWriteAction {
      val text = readText()
      val before = text.substring(0, offset)
      val after = text.substring(offset, text.length)
      writeText(before + string + after)
    }

  protected fun VirtualFile.insertStringAfter(prefix: String, string: String) =
    runWriteAction {
      val text = readText()
      val offset = text.indexOf(prefix)
      val before = text.substring(0, offset)
      val after = text.substring(offset + prefix.length, text.length)
      writeText(before + prefix + string + after)
    }

  protected fun VirtualFile.appendLine(line: String) =
    appendString(line + "\n")

  protected fun VirtualFile.appendString(string: String) =
    runWriteAction { writeText(readText() + string) }

  protected fun VirtualFile.replaceString(old: String, new: String) =
    runWriteAction { writeText(readText().replaceFirst(old, new)) }

  protected fun VirtualFile.delete() =
    runWriteAction { deleteRecursively() }

  protected fun VirtualFile.modify(modificationType: ExternalSystemModificationType = INTERNAL) {
    when (modificationType) {
      INTERNAL -> appendLine(SAMPLE_TEXT)
      ExternalSystemModificationType.EXTERNAL -> appendLineInIoFile(SAMPLE_TEXT)
      ExternalSystemModificationType.HIDDEN -> throw UnsupportedOperationException()
      ExternalSystemModificationType.UNKNOWN -> throw UnsupportedOperationException()
    }
  }

  protected fun VirtualFile.revert() =
    replaceString("$SAMPLE_TEXT\n", "")

  protected fun Document.save() =
    runWriteAction { saveToDisk() }

  protected fun Document.replaceContent(content: String) =
    runWriteAction { setText(content) }

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

  protected fun Document.modify() =
    appendLine(SAMPLE_TEXT)

  protected fun registerSettingsFile(projectAware: MockProjectAware, relativePath: String) {
    projectAware.registerSettingsFile(projectNioPath.getResolvedPath(relativePath))
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

  protected fun assertProjectAware(
    projectAware: MockProjectAware,
    numReload: Int? = null,
    numSettingsAccess: Int? = null,
    numSubscribing: Int? = null,
    numUnsubscribing: Int? = null,
    event: String,
  ) {
    if (numReload != null) assertCountEvent(numReload, projectAware.reloadCounter.get(), "project reload", event)
    if (numSettingsAccess != null) assertCountEvent(numSettingsAccess, projectAware.settingsAccessCounter.get(), "access to settings",
                                                    event)
    if (numSubscribing != null) assertCountEvent(numSubscribing, projectAware.subscribeCounter.get(), "subscribe", event)
    if (numUnsubscribing != null) assertCountEvent(numUnsubscribing, projectAware.unsubscribeCounter.get(), "unsubscribe", event)
  }

  protected fun assertCountEvent(expected: Int, actual: Int, countEvent: String, event: String) {
    val message = when {
      actual > expected -> "Unexpected $countEvent event"
      actual < expected -> "Expected $countEvent event"
      else -> ""
    }
    assertEquals("$message when $event", expected, actual)
  }

  protected fun assertProjectTrackerSettings(autoReloadType: AutoReloadType, event: String) {
    val message = when (autoReloadType) {
      ALL -> "Auto reload must be enabled"
      SELECTIVE -> "Auto reload must be enabled"
      NONE -> "Auto reload must be disabled"
    }
    assertEquals("$message when $event", autoReloadType, projectTrackerSettings.autoReloadType)
  }

  protected fun assertActivationStatus(vararg projects: ExternalSystemProjectId, event: String) {
    val message = when (projects.isEmpty()) {
      true -> "Auto reload must be activated"
      false -> "Auto reload must be deactivated"
    }
    assertEquals("$message when $event", projects.toSet(), projectTracker.getActivatedProjects())
  }

  protected fun assertNotificationAware(vararg projects: ExternalSystemProjectId, event: String) {
    val message = when (projects.isEmpty()) {
      true -> "Notification must be expired"
      else -> "Notification must be notified"
    }
    assertEquals("$message when $event", projects.toSet(), notificationAware.getProjectsWithNotification())
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
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    myProject.replaceService(ExternalSystemProjectTrackerSettings::class.java, AutoImportProjectTrackerSettings(), testDisposable)
    myProject.replaceService(ExternalSystemProjectTracker::class.java, AutoImportProjectTracker(myProject), testDisposable)
  }

  override fun tearDown() {
    try {
      Disposer.dispose(testDisposable)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun withProjectTracker(
    state: Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> =
      AutoImportProjectTracker.State() to AutoImportProjectTrackerSettings.State(),
    test: (Disposable) -> Unit,
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

  protected fun testProjectTrackerState(
    projectAware: MockProjectAware,
    state: Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> =
      AutoImportProjectTracker.State() to AutoImportProjectTrackerSettings.State(),
    test: SimpleTestBench.() -> Unit,
  ): Pair<AutoImportProjectTracker.State, AutoImportProjectTrackerSettings.State> {
    return withProjectTracker(state) {
      register(projectAware, parentDisposable = it)
      SimpleTestBench(projectAware).test()
    }
  }

  protected fun test(relativePath: String = SETTINGS_FILE, test: SimpleTestBench.(VirtualFile) -> Unit) {
    withProjectTracker {
      val projectAware = mockProjectAware()

      register(projectAware, parentDisposable = it)

      SimpleTestBench(projectAware).apply {
        assertStateAndReset(
          numReload = 1,
          numSettingsAccess = 2,
          notified = false,
          numSubscribing = 2,
          numUnsubscribing = 0,
          autoReloadType = SELECTIVE,
          event = "project is registered without cache"
        )

        val settingsFile = createSettingsVirtualFile(relativePath)
        assertStateAndReset(numReload = 0, numSettingsAccess = 1, notified = true, event = "settings file is created")

        scheduleProjectReload()
        assertStateAndReset(numReload = 1, numSettingsAccess = 2, notified = false, event = "project is reloaded")

        test(settingsFile)
      }
    }
  }

  protected fun mockProjectAware(projectId: ExternalSystemProjectId = ExternalSystemProjectId(TEST_EXTERNAL_SYSTEM_ID, projectPath)) =
    MockProjectAware(projectId, myProject, testDisposable)

  protected inner class SimpleTestBench(val projectAware: MockProjectAware) {

    fun markDirty() = markDirty(projectAware.projectId)

    fun forceReloadProject() = projectAware.forceReloadProject()

    fun registerProjectAware() = register(projectAware)

    fun removeProjectAware() = remove(projectAware.projectId)

    fun registerSettingsFile(file: VirtualFile) = projectAware.registerSettingsFile(file)

    fun registerSettingsFile(relativePath: String) = projectAware.registerSettingsFile(projectNioPath.getResolvedPath(relativePath))

    fun ignoreSettingsFileWhen(file: VirtualFile, condition: (ExternalSystemSettingsFilesModificationContext) -> Boolean) =
      projectAware.ignoreSettingsFileWhen(file, condition)

    fun ignoreSettingsFileWhen(relativePath: String, condition: (ExternalSystemSettingsFilesModificationContext) -> Boolean) =
      projectAware.ignoreSettingsFileWhen(projectNioPath.getResolvedPath(relativePath), condition)

    fun whenReloadStarted(parentDisposable: Disposable, action: () -> Unit) =
      projectAware.startReloadEventDispatcher.whenEventHappened(parentDisposable, action)

    fun whenReloadStarted(times: Int, action: () -> Unit) =
      projectAware.startReloadEventDispatcher.whenEventHappened(times, action)

    fun onceWhenReloadStarted(action: () -> Unit) =
      projectAware.startReloadEventDispatcher.onceWhenEventHappened(action)

    fun whenReloading(parentDisposable: Disposable, action: (ExternalSystemProjectReloadContext) -> Unit) =
      projectAware.reloadEventDispatcher.whenEventHappened(parentDisposable, action)

    fun whenReloading(times: Int, action: (ExternalSystemProjectReloadContext) -> Unit) =
      projectAware.reloadEventDispatcher.whenEventHappened(times, action)

    fun onceWhenReloading(action: (ExternalSystemProjectReloadContext) -> Unit) =
      projectAware.reloadEventDispatcher.onceWhenEventHappened(action)

    fun whenReloadFinished(parentDisposable: Disposable, action: (ExternalSystemRefreshStatus) -> Unit) =
      projectAware.finishReloadEventDispatcher.whenEventHappened(parentDisposable, action)

    fun whenReloadFinished(times: Int, action: (ExternalSystemRefreshStatus) -> Unit) =
      projectAware.finishReloadEventDispatcher.whenEventHappened(times, action)

    fun onceWhenReloadFinished(action: (ExternalSystemRefreshStatus) -> Unit) =
      projectAware.finishReloadEventDispatcher.onceWhenEventHappened(action)

    fun setReloadStatus(status: ExternalSystemRefreshStatus) = projectAware.reloadStatus.set(status)

    fun setReloadCollisionPassType(type: ReloadCollisionPassType) = projectAware.reloadCollisionPassType.set(type)

    fun setModificationTypeAdjustingRule(rule: (path: String, type: ExternalSystemModificationType) -> ExternalSystemModificationType) =
      projectAware.modificationTypeAdjustingRule.set(rule)

    fun createSettingsVirtualFile(relativePath: String): VirtualFile {
      registerSettingsFile(relativePath)
      return createFile(relativePath)
    }

    fun withLinkedProject(name: String, relativePath: String, test: SimpleTestBench.(VirtualFile) -> Unit) {
      val projectId = ExternalSystemProjectId(projectAware.projectId.systemId, "$projectPath/$name")
      val projectAware = mockProjectAware(projectId)
      Disposer.newDisposable().use {
        val file = findOrCreateFile("$name/$relativePath")
        projectAware.registerSettingsFile(file)
        register(projectAware, parentDisposable = it)
        SimpleTestBench(projectAware).test(file)
      }
    }

    fun assertStateAndReset(
      numReload: Int? = null,
      numSettingsAccess: Int? = null,
      numSubscribing: Int? = null,
      numUnsubscribing: Int? = null,
      autoReloadType: AutoReloadType = SELECTIVE,
      notified: Boolean,
      event: String,
    ) {
      assertProjectAware(projectAware, numReload, numSettingsAccess, numSubscribing, numUnsubscribing, event)
      assertProjectTrackerSettings(autoReloadType, event = event)
      when (notified) {
        true -> assertNotificationAware(projectAware.projectId, event = event)
        else -> assertNotificationAware(event = event)
      }
      projectAware.resetAssertionCounters()
    }

    fun waitForAllProjectActivities(action: () -> Unit) {
      projectAware.waitForAllProjectActivities(action)
    }
  }

  companion object {
    const val SAMPLE_TEXT = "println 'hello'"

    const val SETTINGS_FILE = "settings.groovy"
  }
}