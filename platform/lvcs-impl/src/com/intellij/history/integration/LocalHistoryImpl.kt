// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.*
import com.intellij.history.core.*
import com.intellij.history.core.changes.Change
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.PutLabelChange
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getInt
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.platform.lvcs.impl.RevisionId
import com.intellij.platform.lvcs.impl.diff.findEntry
import com.intellij.platform.lvcs.impl.operations.getRevertCommandName
import com.intellij.util.SystemProperties
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Throws
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class LocalHistoryImpl(private val coroutineScope: CoroutineScope) : LocalHistoryEx() {
  companion object {
    private const val DAYS_TO_KEEP = "localHistory.daysToKeep"

    /**
     * @see [LocalHistory.getInstance]
     * @see [LocalHistoryEx.facade]
     * @see [IdeaGateway.getInstance]
     */
    @JvmStatic
    fun getInstanceImpl(): LocalHistoryImpl = getInstance() as LocalHistoryImpl

    private fun getProjectId(p: Project): String = p.getLocationHash()
  }

  private var daysToKeep = getInt(DAYS_TO_KEEP)

  override val isEnabled: Boolean
    get() = !isDisabled

  private var isDisabled: Boolean = false

  override var facade: LocalHistoryFacade? = null

  val gateway: IdeaGateway = IdeaGateway.getInstance()

  private var flusherTask: Job? = null
  private val initialFlush = AtomicBoolean(true)

  private var eventDispatcher: LocalHistoryEventDispatcher? = null
  private val isInitialized = AtomicBoolean()

  init {
    init()
  }

  internal fun getEventDispatcher(): LocalHistoryEventDispatcher? {
    return eventDispatcher
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

    ShutDownTracker.getInstance().registerShutdownTask(Runnable { doDispose() })
    initHistory()
    app.getMessageBus().simpleConnect().subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == DAYS_TO_KEEP) {
          daysToKeep = newValue as Int
        }
      }
    })

    flusherTask = coroutineScope.launch {
      while (true) {
        delay(1.seconds)

        val changeList = facade?.changeList ?: continue
        withContext(Dispatchers.IO) {
          if (initialFlush.compareAndSet(true, false)) {
            changeList.purgeObsolete()
          }
          coroutineContext.ensureActive()
          changeList.force()
        }
      }
    }
    isInitialized.set(true)
  }

  private fun initHistory() {
    facade = LocalHistoryFacade()
    eventDispatcher = LocalHistoryEventDispatcher(facade!!, gateway)
  }

  override fun dispose() {
    doDispose()
  }

  private fun doDispose() {
    if (!isInitialized.getAndSet(false)) {
      return
    }

    flusherTask?.let {
      it.cancel()
      flusherTask = null
    }
    facade?.changeList?.close()
    LocalHistoryLog.LOG.debug("Local history storage successfully closed.")
  }

  private fun ChangeList.purgeObsolete() {
    val period = daysToKeep * 1000L * 60L * 60L * 24L
    LocalHistoryLog.LOG.debug("Purging local history...")
    purgeObsolete(period)
  }

  @TestOnly
  fun cleanupForNextTest() {
    doDispose()
    facade?.storageDir?.delete()
    init()
  }

  override fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction {
    if (!isInitialized()) {
      return LocalHistoryAction.NULL
    }

    val a = LocalHistoryActionImpl(eventDispatcher, name, activityId)
    a.start()
    return a
  }

  override fun putEventLabel(project: Project, name: @NlsContexts.Label String, activityId: ActivityId): Label {
    if (!isInitialized()) {
      return Label.NULL_INSTANCE
    }

    val action = startAction(name, activityId)
    val label = label(facade!!.putUserLabel(name, getProjectId(project)))
    action.finish()
    return label
  }

  override fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label {
    return putEventLabel(project, name, CommonActivity.UserLabel)
  }

  override fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label {
    if (!isInitialized()) {
      return Label.NULL_INSTANCE
    }

    gateway.registerUnsavedDocuments(facade!!)
    return label(facade!!.putSystemLabel(name, getProjectId(project), color))
  }

  @ApiStatus.Internal
  fun addVFSListenerAfterLocalHistoryOne(virtualFileListener: BulkFileListener, disposable: Disposable) {
    eventDispatcher!!.addVirtualFileListener(virtualFileListener, disposable)
  }

  private fun label(label: LabelImpl): Label {
    return object : Label {
      override fun revert(project: Project, file: VirtualFile) {
        revertToLabel(project = project, f = file, label = label)
      }

      override fun getByteContent(path: String): ByteContent {
        return ApplicationManager.getApplication().runReadAction(Computable {
          label.getByteContent(gateway.createTransientRootEntryForPath(path, false), path)
        })
      }
    }
  }

  override fun getByteContent(virtualFile: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray? {
    if (!isInitialized()) {
      return null
    }

    return ApplicationManager.getApplication().runReadAction(Computable {
      if (gateway.areContentChangesVersioned(virtualFile)) {
        ByteContentRetriever(gateway, facade, virtualFile, condition).getResult()
      }
      else {
        null
      }
    })
  }

  override fun isUnderControl(file: VirtualFile): Boolean = isInitialized() && gateway.isVersioned(file)

  private fun isInitialized(): Boolean = isInitialized.get()

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

    val rootEntry = runReadAction { gateway.createTransientRootEntryForPaths(targetPaths, true) }
    val leftEntry = facade!!.findEntry(rootEntry, RevisionId.ChangeSet(targetChangeSet!!.id), path,
                                       /*do not revert the change itself*/false)
    val rightEntry = rootEntry.findEntry(path)
    val diff = Entry.getDifferencesBetween(leftEntry, rightEntry, true)
    if (diff.isEmpty()) return // nothing to revert

    val reverter = DifferenceReverter(project, facade, gateway, diff) {
      getRevertCommandName((targetChange as? PutLabelChange)?.name, targetChangeSet!!.timestamp, false)
    }
    try {
      reverter.revert()
    }
    catch (e: Exception) {
      throw LocalHistoryException("Couldn't revert ${f.getName()} to local history label.", e)
    }
  }
}
