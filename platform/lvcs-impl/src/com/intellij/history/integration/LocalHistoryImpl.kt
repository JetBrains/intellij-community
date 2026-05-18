// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.ActivityId
import com.intellij.history.ByteContent
import com.intellij.history.FileRevisionTimestampComparator
import com.intellij.history.Label
import com.intellij.history.LocalHistoryAction
import com.intellij.history.LocalHistoryException
import com.intellij.history.core.ByteContentRetriever
import com.intellij.history.core.ChangeAndPathProcessor
import com.intellij.history.core.ChangeListImpl
import com.intellij.history.core.InMemoryChangeListStorage
import com.intellij.history.core.LabelImpl
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.PersistentChangeListStorage
import com.intellij.history.core.changes.Change
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.PutLabelChange
import com.intellij.history.core.collectChanges
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.platform.lvcs.impl.RevisionId
import com.intellij.platform.lvcs.impl.diff.findEntry
import com.intellij.platform.lvcs.impl.operations.getRevertCommandName
import com.intellij.util.SystemProperties
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class LocalHistoryImpl(private val coroutineScope: CoroutineScope) : LocalHistoryEx() {
  companion object {
    /**
     * @see [LocalHistory.getInstance]
     * @see [LocalHistoryEx.facade]
     * @see [IdeaGateway.getInstance]
     */
    @JvmStatic
    fun getInstanceImpl(): LocalHistoryImpl = getInstance() as LocalHistoryImpl

    private fun getProjectId(p: Project): String = p.getLocationHash()
  }

  override val isEnabled: Boolean
    get() = !isDisabled

  private var isDisabled: Boolean = false

  private val state = AtomicReference<State>(State.Initializing)
  private val stateIfInitialized: State.Initialized?
    get() = state.get().asSafely<State.Initialized>()

  private fun isInitialized(): Boolean = stateIfInitialized != null

  override val facade: LocalHistoryFacade?
    get() = stateIfInitialized?.facade

  val gateway: IdeaGateway = IdeaGateway.getInstance()

  init {
    init()
  }

  internal fun getEventDispatcher(): LocalHistoryEventDispatcher? {
    return stateIfInitialized?.eventDispatcher
  }

  private fun init() {
    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode() && app.isHeadlessEnvironment()) {
      return
    }

    // too early for Registry
    if (SystemProperties.getBooleanProperty("lvcs.disable.local.history", false)) {
      LocalHistoryLog.LOG.warn("Local history is disabled")
      isDisabled = true
      return
    }

    ShutDownTracker.getInstance().registerShutdownTask(Runnable { doDispose(drop = false) })

    val storage = try {
      val storageDir = PathManager.getSystemDir().resolve("LocalHistory")
      PersistentChangeListStorage(storageDir)
    }
    catch (e: Throwable) {
      LocalHistoryLog.LOG.warn("cannot create storage, in-memory implementation will be used", e)
      InMemoryChangeListStorage()
    }
    val changeList = ChangeListImpl(storage)
    val flusherTask = changeList.launchFlusher()
    val facade = LocalHistoryFacade(changeList)
    val eventDispatcher = LocalHistoryEventDispatcher(facade, gateway)

    registerDeletionHandler(facade)
    state.set(State.Initialized(changeList, flusherTask, facade, eventDispatcher))
  }

  private fun ChangeListImpl.launchFlusher(): Job =
    coroutineScope.launch {
      while (true) {
        delay(1.seconds)
        flush()
      }
    }

  private fun registerDeletionHandler(facade: LocalHistoryFacade) {
    val deletionHandler = LocalHistoryFilesDeletionHandler(facade, gateway)
    LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(deletionHandler)
    Disposer.register(this) {
      LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(deletionHandler)
    }
  }

  override fun dispose() {
    doDispose(drop = false)
  }

  private fun doDispose(drop: Boolean) {
    val state = state.getAndSet(State.Disposed)
    if (state !is State.Initialized) {
      return
    }
    state.flusherTask.cancel()
    state.changeList.close(drop)
    LocalHistoryLog.LOG.debug("Local history storage successfully closed.")
  }

  @TestOnly
  fun cleanupForNextTest() {
    doDispose(drop = true)
    init()
  }

  override fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction {
    val eventDispatcher = stateIfInitialized?.eventDispatcher ?: return LocalHistoryAction.NULL

    val a = LocalHistoryActionImpl(eventDispatcher, name, activityId)
    a.start()
    return a
  }

  override fun putEventLabel(project: Project, name: @NlsContexts.Label String, activityId: ActivityId): Label {
    val facade = stateIfInitialized?.facade ?: return Label.NULL_INSTANCE

    val action = startAction(name, activityId)
    val label = label(facade.putUserLabel(name, getProjectId(project)))
    action.finish()
    return label
  }

  override fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label {
    return putEventLabel(project, name, CommonActivity.UserLabel)
  }

  override fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label {
    val facade = stateIfInitialized?.facade ?: return Label.NULL_INSTANCE

    gateway.registerUnsavedDocuments(facade)
    return label(facade.putSystemLabel(name, getProjectId(project), color))
  }

  @ApiStatus.Internal
  fun addVFSListenerAfterLocalHistoryOne(virtualFileListener: BulkFileListener, disposable: Disposable) {
    (state.get() as State.Initialized).eventDispatcher.addVirtualFileListener(virtualFileListener, disposable)
  }

  private fun label(label: LabelImpl): Label {
    return object : Label {
      override fun revert(project: Project, file: VirtualFile) {
        revertToLabel(project = project, f = file, label = label)
      }

      override fun getByteContent(path: String): ByteContent {
        return runReadActionBlocking {
          label.getByteContent(gateway.createTransientRootEntryForPath(path, false), path)
        }
      }
    }
  }

  override fun getByteContent(file: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray? {
    if (!isInitialized()) {
      return null
    }

    return runReadActionBlocking {
      if (gateway.areContentChangesVersioned(file)) {
        ByteContentRetriever(gateway, facade, file, condition).getResult()
      }
      else {
        null
      }
    }
  }

  override fun isUnderControl(file: VirtualFile): Boolean = isInitialized() && gateway.isVersioned(file)

  @Throws(LocalHistoryException::class)
  private fun revertToLabel(project: Project, f: VirtualFile, label: LabelImpl) {
    val path = gateway.getPathOrUrl(f)

    var targetChangeSet: ChangeSet? = null
    var targetChange: Change? = null
    val targetPaths = mutableSetOf(path)

    facade!!.collectChanges(path, ChangeAndPathProcessor(project.locationHash, null, targetPaths::add) { changeSet: ChangeSet ->
      val change = changeSet.changes.firstOrNull { it.id == label.labelChangeId }
      if (change != null) {
        targetChangeSet = changeSet
        targetChange = change
      }
    })

    if (targetChangeSet == null || targetChange == null) {
      throw LocalHistoryException("Couldn't find label")
    }

    val rootEntry = runReadActionBlocking { gateway.createTransientRootEntryForPaths(targetPaths, true) }
    val leftEntry = facade!!.findEntry(rootEntry, RevisionId.ChangeSet(targetChangeSet.id), path,
                                       /*do not revert the change itself*/false)
    val rightEntry = rootEntry.findEntry(path)
    val diff = Entry.getDifferencesBetween(leftEntry, rightEntry, true)
    if (diff.isEmpty()) return // nothing to revert

    val reverter = DifferenceReverter(project, facade, gateway, diff) {
      getRevertCommandName((targetChange as? PutLabelChange)?.name, targetChangeSet.timestamp, false)
    }
    try {
      reverter.revert()
    }
    catch (e: Exception) {
      throw LocalHistoryException("Couldn't revert ${f.getName()} to local history label.", e)
    }
  }
}

private sealed interface State {
  object Initializing : State
  class Initialized(
    val changeList: ChangeListImpl,
    val flusherTask: Job,
    val facade: LocalHistoryFacade,
    val eventDispatcher: LocalHistoryEventDispatcher,
  ) : State

  object Disposed : State
}
