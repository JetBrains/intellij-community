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
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.PersistentChangeListStorage
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.PutLabelChange
import com.intellij.history.core.collectChanges
import com.intellij.history.core.tree.Entry
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.integration.revertion.Reverter
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
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
    val label = facade.putUserLabel(name, getProjectId(project))
    action.finish()
    return createLabelWrapper(label.id)
  }

  override fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label {
    return putEventLabel(project, name, CommonActivity.UserLabel)
  }

  override fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label {
    val facade = stateIfInitialized?.facade ?: return Label.NULL_INSTANCE

    gateway.registerUnsavedDocuments(facade)
    val label = facade.putSystemLabel(name, getProjectId(project), color)
    return createLabelWrapper(label.id)
  }

  override suspend fun isLabelValid(project: Project, labelId: String): Boolean {
    if (labelId == Label.NON_EXISTENT_ID) return false
    val rawLabelId = labelId.toLongOrNull() ?: return false
    val facade = facade ?: return false

    val projectId = getProjectId(project)

    return withContext(Dispatchers.Default) {
      facade.changes.any { changesSet ->
        changesSet.changes.any {
          it is PutLabelChange && it.affectsProject(projectId) && it.id == rawLabelId
        }
      }
    }
  }

  override suspend fun revertToLabel(project: Project, labelId: String, file: VirtualFile) {
    require(labelId != Label.NON_EXISTENT_ID) { "Received an ID of a null label. Please validate that the label was created." }
    val rawLabelId = labelId.toLongOrNull() ?: throw IllegalArgumentException("Invalid label ID: $labelId")
    val facade = facade ?: throw LocalHistoryException(CANNOT_REVERT_NO_HISTORY_ERROR)
    facade.revertToLabel(project, file, rawLabelId)
  }

  @ApiStatus.Internal
  fun addVFSListenerAfterLocalHistoryOne(virtualFileListener: BulkFileListener, disposable: Disposable) {
    (state.get() as State.Initialized).eventDispatcher.addVirtualFileListener(virtualFileListener, disposable)
  }

  private fun createLabelWrapper(labelId: Long): Label {
    return object : Label {
      override val id: String = labelId.toString()

      override fun revert(project: Project, file: VirtualFile) {
        val facade = facade ?: error(CANNOT_REVERT_NO_HISTORY_ERROR)
        facade.revertToLabelBlocking(project, file, labelId)
      }

      override fun getByteContent(path: String): ByteContent {
        val facade = facade ?: error("Local history storage unavailable")
        val root = runReadActionBlocking {
          gateway.createTransientRootEntryForPath(path, false)
        }
        return facade.getByteContentBefore(root, path, labelId)
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
  private suspend fun LocalHistoryFacade.revertToLabel(project: Project, file: VirtualFile, labelId: Long) {
    withContext(Dispatchers.Default) {
      revertToLabel(project, file, labelId, { paths ->
        readAction {
          gateway.createTransientRootEntryForPaths(paths, true)
        }
      }) {
        performRevert()
      }
    }
  }

  @Throws(LocalHistoryException::class)
  private fun LocalHistoryFacade.revertToLabelBlocking(project: Project, file: VirtualFile, labelId: Long) {
    revertToLabel(project, file, labelId, { paths ->
      runReadActionBlocking {
        gateway.createTransientRootEntryForPaths(paths, true)
      }
    }) {
      revert()
    }
  }

  @Throws(LocalHistoryException::class)
  private inline fun LocalHistoryFacade.revertToLabel(
    project: Project,
    file: VirtualFile,
    labelId: Long,
    createRootEntry: (Set<String>) -> RootEntry,
    executeRevert: Reverter.() -> Unit,
  ) {
    val path = gateway.getPathOrUrl(file)

    var targetChangeSet: ChangeSet? = null
    var targetChange: PutLabelChange? = null
    val targetPaths = mutableSetOf(path)

    // TODO: stop collecting when the label change is found
    val projectId = getProjectId(project)
    collectChanges(path, ChangeAndPathProcessor(projectId, null, targetPaths::add) { changeSet: ChangeSet ->
      val change = changeSet.changes.firstOrNull { it.id == labelId }
      if (change != null && change is PutLabelChange) {
        targetChangeSet = changeSet
        targetChange = change
      }
    })

    if (targetChangeSet == null || targetChange == null) {
      throw LocalHistoryException("Couldn't find label with ID $labelId")
    }

    val rootEntry = createRootEntry(targetPaths)
    val leftEntry = findEntry(rootEntry, RevisionId.ChangeSet(targetChangeSet.id), path,
                              /*do not revert the change itself*/false)
    val rightEntry = rootEntry.findEntry(path)
    val diff = Entry.getDifferencesBetween(leftEntry, rightEntry, true)
    if (diff.isEmpty()) return // nothing to revert

    val reverter = DifferenceReverter(project, gateway, diff) {
      getRevertCommandName(targetChange.name, targetChangeSet.timestamp, false)
    }
    try {
      reverter.executeRevert()
    }
    catch (e: Exception) {
      throw LocalHistoryException("Couldn't revert ${file.getName()} to local history label ${targetChange.name}.", e)
    }
  }
}

@VisibleForTesting
internal fun LocalHistoryFacade.getByteContentBefore(root: RootEntry, path: String, changeId: Long): ByteContent {
  return findEntry(root, changeId, path, false)?.getByteContent()
         ?: ByteContent(false, null)
}

private fun Entry.getByteContent(): ByteContent {
  if (isDirectory) return ByteContent(true, null)
  return ByteContent(false, content.bytesIfAvailable)
}

private const val CANNOT_REVERT_NO_HISTORY_ERROR = "Cannot revert to label: local history unavailable"

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
