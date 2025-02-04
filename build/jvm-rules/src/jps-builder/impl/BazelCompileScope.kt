@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.FileDeletedEvent
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent
import org.jetbrains.jps.incremental.messages.ProgressMessage
import java.nio.file.Path
import java.util.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

internal class BazelCompileScope(
  @JvmField val isIncrementalCompilation: Boolean,
  @JvmField val isRebuild: Boolean,
) : CompileScope() {
  private val typesToForceBuild = HashSet<BuildTargetType<*>?>()

  init {
    var forceBuildAllModuleBasedTargets = false
    for (type in typesToForceBuild) {
      typesToForceBuild.add(type)
      forceBuildAllModuleBasedTargets = false
    }
  }

  override fun isAffected(target: BuildTarget<*>): Boolean = isWholeTargetAffected(target)

  override fun isWholeTargetAffected(target: BuildTarget<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isAllTargetsOfTypeAffected(type: BuildTargetType<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isBuildForced(target: BuildTarget<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isBuildForcedForAllTargets(targetType: BuildTargetType<*>): Boolean = !isIncrementalCompilation || isRebuild

  override fun isBuildIncrementally(targetType: BuildTargetType<*>): Boolean = isIncrementalCompilation && !isRebuild

  override fun isAffected(target: BuildTarget<*>, file: Path): Boolean = true

  override fun markIndirectlyAffected(target: BuildTarget<*>?, file: Path) {
  }
}

internal class BazelCompileContext(
  private val scope: BazelCompileScope,
  private val projectDescriptor: ProjectDescriptor,
  private val delegateMessageHandler: MessageHandler,
  private val coroutineContext: CoroutineContext,
) : UserDataHolderBase(), CompileContext {
  private val cancelStatus = CanceledStatus { !coroutineContext.isActive }

  private var isMarkedAsNonIncremental = false
  @Volatile
  private var compilationStartStamp = 0L

  @Volatile
  private var done = -1.0f
  private val listeners = EventDispatcher.create(BuildListener::class.java)

  override fun getCompilationStartStamp(target: BuildTarget<*>): Long = compilationStartStamp

  override fun setCompilationStartStamp(targets: Collection<BuildTarget<*>>, stamp: Long) {
    compilationStartStamp = stamp
  }

  override fun getLoggingManager(): BuildLoggingManager = projectDescriptor.loggingManager

  override fun getBuilderParameter(paramName: String?): String? = null

  override fun addBuildListener(listener: BuildListener) {
    listeners.addListener(listener)
  }

  override fun removeBuildListener(listener: BuildListener) {
    listeners.removeListener(listener)
  }

  override fun markNonIncremental(target: ModuleBuildTarget) {
    isMarkedAsNonIncremental = true
  }

  override fun shouldDifferentiate(chunk: ModuleChunk): Boolean = scope.isIncrementalCompilation && !isMarkedAsNonIncremental

  override fun getCancelStatus(): CanceledStatus = cancelStatus

  override fun checkCanceled() {
    coroutineContext.ensureActive()
  }

  override fun clearNonIncrementalMark(target: ModuleBuildTarget) {
    isMarkedAsNonIncremental = false
  }

  override fun getScope(): BazelCompileScope = scope

  override fun processMessage(message: BuildMessage) {
    if (message.kind == BuildMessage.Kind.ERROR) {
      Utils.ERRORS_DETECTED_KEY.set(this, true)
    }
    if (message is ProgressMessage) {
      message.done = done
    }
    delegateMessageHandler.processMessage(message)
    if (message is FileGeneratedEvent) {
      listeners.getMulticaster().filesGenerated(message)
    }
    else if (message is FileDeletedEvent) {
      listeners.getMulticaster().filesDeleted(message)
    }
  }

  override fun setDone(done: Float) {
    this.done = done
  }

  override fun getProjectDescriptor(): ProjectDescriptor = projectDescriptor
}