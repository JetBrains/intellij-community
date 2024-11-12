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
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.ThreeState
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
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

  private fun <T> iterate(position: SourcePosition, defaultValue: T?, processor: (PositionManager) -> T?): T? {
    val fileType = position.getFile().getFileType()
    return iterate<T>(defaultValue, fileType, true, processor)
  }

  private fun <T> iterate(defaultValue: T?, fileType: FileType?, ignorePCE: Boolean, processor: (PositionManager) -> T?): T? {
    for (positionManager in myPositionManagers) {
      if (!acceptsFileType(positionManager, fileType)) continue
      try {
        if (!ignorePCE) {
          ProgressManager.checkCanceled()
        }
        return DebuggerUtilsImpl.suppressExceptions<T?, NoDataException?>(
          { processor(positionManager) },
          defaultValue, ignorePCE, NoDataException::class.java)
      }
      catch (_: NoDataException) {
      }
    }
    return defaultValue
  }

  private fun <T> iterateAsync(
    fileType: FileType?,
    ignorePCE: Boolean,
    processor: (PositionManager) -> CompletableFuture<T?>,
  ): CompletableFuture<T?> {
    var res = CompletableFuture.failedFuture<T?>(NoDataException.INSTANCE)
    for (positionManager in myPositionManagers) {
      if (!acceptsFileType(positionManager, fileType)) continue
      res = res.exceptionallyCompose { e: Throwable ->
        val unwrap = DebuggerUtilsAsync.unwrap(e)
        if (unwrap is NoDataException) {
          if (!ignorePCE) {
            ProgressManager.checkCanceled()
          }
          return@exceptionallyCompose processor(positionManager)
        }
        CompletableFuture.failedFuture<T?>(unwrap)
      }
    }
    return res
  }

  override fun getSourcePositionAsync(location: Location?): CompletableFuture<SourcePosition?> =
    getCachedSourcePosition(location) { fileType: FileType? ->
      iterateAsync<SourcePosition?>(fileType, false) { getSourcePositionAsync(it, location) }
    }

  private fun getCachedSourcePosition(
    location: Location?,
    producer: (FileType?) -> CompletableFuture<SourcePosition?>,
  ): CompletableFuture<SourcePosition?> {
    if (location == null) return CompletableFuture.completedFuture<SourcePosition?>(null)
    var res: SourcePosition? = null
    try {
      res = mySourcePositionCache[location]
    }
    catch (_: IllegalArgumentException) { // Invalid method id
    }
    if (checkCacheEntry(res, location)) return CompletableFuture.completedFuture<SourcePosition?>(res)

    val fileType = runReadAction {
      val sourceName = DebuggerUtilsEx.getSourceName(location, null)
      if (sourceName != null) FileTypeManager.getInstance().getFileTypeByFileName(sourceName) else null
    }
    return producer(fileType)
      .thenApply<SourcePosition?> { p: SourcePosition? ->
        try {
          mySourcePositionCache.put(location, p)
        }
        catch (_: IllegalArgumentException) { // Invalid method id
        }
        p
      }
  }

  override fun getSourcePosition(location: Location?): SourcePosition? {
    return getCachedSourcePosition(location) { fileType: FileType? ->
      val sourcePosition = ReadAction.nonBlocking<SourcePosition?> {
        iterate<SourcePosition?>(null, fileType, false) { it.getSourcePosition(location) }
      }.executeSynchronously()
      CompletableFuture.completedFuture<SourcePosition?>(sourcePosition)
    }.getNow(null)
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

  fun createStackFrames(descriptor: StackFrameDescriptorImpl): MutableList<XStackFrame>? =
    iterate(null, null, false) {
      if (it is PositionManagerWithMultipleStackFrames) {
        val stackFrames = it.createStackFrames(descriptor)
        if (stackFrames != null) {
          return@iterate stackFrames
        }
      }
      else if (it is PositionManagerEx) {
        val xStackFrame = it.createStackFrame(descriptor)
        if (xStackFrame != null) {
          return@iterate mutableListOf(xStackFrame)
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

private fun getSourcePositionAsync(positionManager: PositionManager, location: Location?): CompletableFuture<SourcePosition?> {
  if (positionManager is PositionManagerAsync) {
    return positionManager.getSourcePositionAsync(location)
  }
  try {
    val sourcePosition = ReadAction.nonBlocking<SourcePosition?> { positionManager.getSourcePosition(location) }.executeSynchronously()
    return CompletableFuture.completedFuture<SourcePosition?>(sourcePosition)
  }
  catch (e: Exception) {
    return CompletableFuture.failedFuture<SourcePosition?>(DebuggerUtilsAsync.unwrap(e))
  }
}

private fun checkCacheEntry(position: SourcePosition?, location: Location): Boolean {
  if (position == null) return false
  return runReadAction {
    val psiFile = position.getFile()
    if (!psiFile.isValid()) return@runReadAction false
    val url = DebuggerUtilsEx.getAlternativeSourceUrl(location.declaringType().name(), psiFile.getProject()) ?: return@runReadAction true
    val file = psiFile.getVirtualFile()
    file != null && url == file.url
  }
}
