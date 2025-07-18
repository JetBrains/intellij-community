// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.NoDataException
import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerManagerThreadImpl.Companion.assertIsManagerThread
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.suppressExceptions
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.util.ThreeState
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.CompletableFuture

class CompoundPositionManager() : PositionManagerWithConditionEvaluation, MultiRequestPositionManager, PositionManagerAsync {
  private val myPositionManagers = mutableListOf<PositionManager>()
  private val mySourcePositionCache: MutableMap<Location?, SourcePosition?> = WeakHashMap<Location?, SourcePosition?>()

  constructor(manager: PositionManager) : this() {
    appendPositionManager(manager)
  }

  fun appendPositionManager(manager: PositionManager) {
    myPositionManagers.remove(manager)
    myPositionManagers.add(0, manager)
    clearCache()
  }

  fun removePositionManager(manager: PositionManager) {
    myPositionManagers.remove(manager)
    clearCache()
  }

  fun clearCache() {
    assertIsManagerThread()
    mySourcePositionCache.clear()
  }

  private inline fun <T> iterate(position: SourcePosition, defaultValue: T?, processor: (PositionManager) -> T?): T? {
    val fileType = position.getFile().getFileType()
    return iterate<T>(defaultValue, fileType, ProgressManager::checkCanceled, processor)
  }

  private inline fun <T> iterate(
    defaultValue: T?, fileType: FileType?,
    cancellationCheck: () -> Unit,
    processor: (PositionManager) -> T?,
  ): T? {
    val isCancellableSection = DebuggerManagerThreadImpl.hasNonDefaultProgressIndicator()
    for (positionManager in myPositionManagers) {
      if (!acceptsFileType(positionManager, fileType)) continue
      if (isCancellableSection) {
        cancellationCheck()
      }
      try {
        return suppressExceptions(defaultValue, NoDataException::class.java) { processor(positionManager) }
      }
      catch (_: NoDataException) {
      }
    }
    return defaultValue
  }

  fun getSourcePositionFuture(location: Location?): CompletableFuture<SourcePosition?> =
    DebuggerUtilsAsync.reschedule(invokeCommandAsCompletableFuture { getSourcePositionAsync(location) })

  override suspend fun getSourcePositionAsync(location: Location?): SourcePosition? =
    getCachedSourcePosition(location, { action -> readAction(action) }) { fileType: FileType? ->
      iterate<SourcePosition?>(null, fileType, { checkCanceled() }) { getSourcePositionAsync(it, location) }
    }

  override fun getSourcePosition(location: Location?): SourcePosition? =
    getCachedSourcePosition(location, { action -> runReadAction(action) }) { fileType: FileType? ->
      ReadAction.nonBlocking<SourcePosition?> {
        iterate<SourcePosition?>(null, fileType, ProgressManager::checkCanceled) { it.getSourcePosition(location) }
      }.executeSynchronously()
    }

  private inline fun getCachedSourcePosition(
    location: Location?,
    insideReadAction: (() -> Unit) -> Unit,
    producer: (FileType?) -> SourcePosition?,
  ): SourcePosition? {
    if (location == null) return null
    try {
      val position = mySourcePositionCache[location]
      if (position != null && checkCacheEntry(position, location.declaringType().name(), insideReadAction)) {
        return position
      }
    }
    catch (_: IllegalArgumentException) { // Invalid method id
    }

    val sourceName = DebuggerUtilsEx.getSourceName(location, null)
    val fileType = if (sourceName != null)
      callInReadAction(insideReadAction) { FileTypeManager.getInstance().getFileTypeByFileName(sourceName) }
    else
      null

    val position = producer(fileType)
    try {
      mySourcePositionCache.put(location, position)
    }
    catch (_: IllegalArgumentException) { // Invalid method id
    }
    return position
  }

  override fun getAllClasses(classPosition: SourcePosition): MutableList<ReferenceType?> =
    iterate(classPosition, mutableListOf<ReferenceType?>()) { it.getAllClasses(classPosition) }!!

  override fun locationsOfLine(type: ReferenceType, position: SourcePosition): MutableList<Location> {
    var position = position
    val file = position.getFile().getVirtualFile()
    if (file != null) {
      val mapping = file.getUserData<LineNumbersMapping?>(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)
      if (mapping != null) {
        val line = mapping.sourceToBytecode(position.getLine() + 1)
        if (line > -1) {
          position = SourcePosition.createFromLine(position.getFile(), line - 1)
        }
      }
    }

    val finalPosition = position
    return iterate(position, mutableListOf<Location>()) { it.locationsOfLine(type, finalPosition) }!!
  }

  override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? =
    iterate(position, null) { it.createPrepareRequest(requestor, position) }

  override fun createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): MutableList<ClassPrepareRequest?> =
    iterate(position, mutableListOf<ClassPrepareRequest?>()) {
      if (it is MultiRequestPositionManager) {
        it.createPrepareRequests(requestor, position)
      }
      else {
        val prepareRequest = it.createPrepareRequest(requestor, position) ?: return@iterate mutableListOf()
        mutableListOf(prepareRequest)
      }
    }!!

  @ApiStatus.Internal
  fun createStackFrames(descriptor: StackFrameDescriptorImpl): List<XStackFrame>? =
    iterate(null, null, ProgressManager::checkCanceled) { positionManager ->
      createStackFramesInternal(positionManager, descriptor) { createStackFrames(it) }
    }

  @ApiStatus.Internal
  fun createStackFramesAsync(descriptor: StackFrameDescriptorImpl): CompletableFuture<List<XStackFrame>?> =
    invokeCommandAsCompletableFuture {
      iterate(null, null, { checkCanceled() }) { positionManager ->
        createStackFramesInternal(positionManager, descriptor) { createStackFramesAsync(it) }
      }
    }

  private inline fun createStackFramesInternal(
    manager: PositionManager,
    descriptor: StackFrameDescriptorImpl,
    extractMultipleFrames: PositionManagerWithMultipleStackFrames.(StackFrameDescriptorImpl) -> List<XStackFrame>?,
  ): List<XStackFrame> {
    if (manager is PositionManagerWithMultipleStackFrames) {
      val stackFrames = manager.extractMultipleFrames(descriptor)
      if (stackFrames != null) {
        return stackFrames
      }
    }
    else if (manager is PositionManagerEx) {
      val xStackFrame = manager.createStackFrame(descriptor)
      if (xStackFrame != null) {
        return mutableListOf(xStackFrame)
      }
    }
    throw NoDataException.INSTANCE
  }

  override fun evaluateCondition(
    context: EvaluationContext,
    frame: StackFrameProxyImpl,
    location: Location,
    expression: String,
  ): ThreeState? {
    for (positionManager in myPositionManagers) {
      if (positionManager !is PositionManagerWithConditionEvaluation) continue
      try {
        val result = positionManager.evaluateCondition(context, frame, location, expression)
        if (result != ThreeState.UNSURE) {
          return result
        }
      }
      catch (e: Throwable) {
        DebuggerUtilsImpl.logError(e)
      }
    }
    return ThreeState.UNSURE
  }

  companion object {
    @JvmField
    val EMPTY: CompoundPositionManager = CompoundPositionManager()
  }
}

private fun acceptsFileType(positionManager: PositionManager, fileType: FileType?): Boolean {
  if (fileType == null || fileType === UnknownFileType.INSTANCE) return true
  val types = positionManager.acceptedFileTypes ?: return true
  return types.contains(fileType)
}

private suspend fun getSourcePositionAsync(positionManager: PositionManager, location: Location?): SourcePosition? {
  if (positionManager is PositionManagerAsync) {
    return positionManager.getSourcePositionAsync(location)
  }
  try {
    return ReadAction.nonBlocking<SourcePosition?> { positionManager.getSourcePosition(location) }.executeSynchronously()
  }
  catch (e: Exception) {
    throw DebuggerUtilsAsync.unwrap(e)
  }
}

private inline fun checkCacheEntry(position: SourcePosition, className: String, insideReadAction: (() -> Unit) -> Unit): Boolean =
  callInReadAction(insideReadAction) {
    val psiFile = position.getFile()
    if (!psiFile.isValid()) return@callInReadAction false
    val url = DebuggerUtilsEx.getAlternativeSourceUrl(className, psiFile.getProject()) ?: return@callInReadAction true
    val file = psiFile.getVirtualFile()
    file != null && url == file.url
}

@Suppress("UNCHECKED_CAST")
private inline fun <T : Any?> callInReadAction(insideReadAction: (() -> Unit) -> Unit, crossinline action: () -> T): T {
  var result: T? = null
  insideReadAction { result = action() }
  return result as T
}
