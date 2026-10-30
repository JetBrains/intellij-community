// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.NoDataException
import com.intellij.debugger.PositionManagerAsync
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.containers.ContainerUtil
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ClassNotPreparedException
import com.sun.jdi.Location
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Unmodifiable
import java.io.File
import java.util.Collections
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author Eugene Zhuravlev
 */
@Suppress("PropertyName")
abstract class JSR45PositionManager<Scope> @Deprecated(
  "Use the constructor without file types and override isAcceptedFileType."
) constructor(
  @JvmField protected val myDebugProcess: DebugProcess,
  @JvmField protected val myScope: Scope,
  @get:JvmName("getStratumIdForKotlin")
  @get:JvmSynthetic
  val stratumId: String?,
  acceptedFileTypes: Array<out LanguageFileType?>?,
  @JvmField protected val mySourcesFinder: SourcesFinder<Scope>,
) : PositionManagerAsync {
  @JvmField
  protected val GENERATED_CLASS_PATTERN: String

  @JvmField
  protected val myGeneratedClassPatternMatcher: Matcher
  private val myFileTypes: Set<LanguageFileType?>? =
    if (acceptedFileTypes == null) null else Collections.unmodifiableSet(ContainerUtil.newHashSet(*acceptedFileTypes))

  @Suppress("DEPRECATION")
  constructor(debugProcess: DebugProcess, scope: Scope, stratumId: String?, sourcesFinder: SourcesFinder<Scope>) : this(
    debugProcess,
    scope,
    stratumId,
    null,
    sourcesFinder
  )

  init {
    val generatedClassesPackage = getGeneratedClassesPackage()
    val generatedClassPattern = if (generatedClassesPackage.isEmpty()) {
      getGeneratedClassesNamePattern()
    }
    else {
      generatedClassesPackage + "." + getGeneratedClassesNamePattern()
    }
    GENERATED_CLASS_PATTERN = generatedClassPattern
    myGeneratedClassPatternMatcher = Pattern.compile(generatedClassPattern.replace("\\*".toRegex(), ".*")).matcher("")
  }

  protected abstract fun getGeneratedClassesPackage(): @NonNls String

  protected open fun getGeneratedClassesNamePattern(): String {
    return "*"
  }

  fun getStratumId(): String? {
    return stratumId
  }

  @RequiresBlockingContext
  @Throws(NoDataException::class)
  override fun getSourcePosition(location: Location?): SourcePosition? {
    return super.getSourcePosition(location)
  }

  @RequiresBackgroundThread
  override suspend fun getSourcePositionAsync(location: Location?): SourcePosition? {
    ThreadingAssertions.assertBackgroundThread()

    if (location == null) {
      throw NoDataException.INSTANCE
    }

    val sourcePosition = try {
      val sourcePath = getRelativeSourcePathByLocation(location)
      val lineNumber = getLineNumber(location)
      readAction {
        mySourcesFinder.findSourceFile(sourcePath, myDebugProcess.project, myScope)?.let { file ->
          SourcePosition.createFromLine(file, lineNumber - 1)
        }
      }
    }
    catch (_: AbsentInformationException) { // ignored
      null
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.info(e)
      null
    }
    return sourcePosition ?: throw NoDataException.INSTANCE
  }

  @Throws(AbsentInformationException::class)
  protected open fun getRelativeSourcePathByLocation(location: Location): String? {
    return getRelativePath(location.sourcePath(stratumId))
  }

  protected open fun getLineNumber(location: Location): Int {
    return location.lineNumber(getStratumId())
  }

  @RequiresBackgroundThread
  @Throws(NoDataException::class)
  override fun getAllClasses(classPosition: SourcePosition): List<ReferenceType> {
    checkSourcePositionFileType(classPosition)

    val referenceTypes = VirtualMachineProxy.getCurrent().allClasses()

    val result = ArrayList<ReferenceType>()

    for (referenceType in referenceTypes) {
      myGeneratedClassPatternMatcher.reset(referenceType.name())
      if (myGeneratedClassPatternMatcher.matches()) {
        val locations = locationsOfClassAt(referenceType, classPosition)
        if (!locations.isNullOrEmpty()) {
          result.add(referenceType)
        }
      }
    }

    return result
  }

  @Deprecated("Use isAcceptedFileType instead.")
  override fun getAcceptedFileTypes(): Set<LanguageFileType?> {
    checkNotNull(myFileTypes) { "Either pass file types to constructor or override getAcceptedFileTypes() method" }
    return myFileTypes
  }

  override fun isAcceptedFileType(fileType: FileType): Boolean {
    val fileTypes: Set<FileType?> = checkNotNull(myFileTypes) {
      "Either pass file types to constructor or override isAcceptedFileType(FileType) method"
    }
    return fileTypes.contains(fileType)
  }

  @Throws(NoDataException::class)
  private fun checkSourcePositionFileType(classPosition: SourcePosition) {
    val fileType = classPosition.getFile().getFileType()
    if (!isAcceptedFileType(fileType)) {
      throw NoDataException.INSTANCE
    }
  }

  @Throws(NoDataException::class)
  override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
    val locations = locationsOfClassAt(type, position)
    return locations ?: emptyList()
  }

  @RequiresBackgroundThread
  @Throws(NoDataException::class)
  private fun locationsOfClassAt(type: ReferenceType, position: SourcePosition): List<Location>? {
    checkSourcePositionFileType(position)

    try {
      val relativePaths = getRelativeSourePathsByType(type)
      for (relativePath in relativePaths) {
        val sourceInfo = ReadAction.nonBlocking<Pair<String, Int>?> {
          val file = mySourcesFinder.findSourceFile(relativePath, myDebugProcess.project, myScope)
          if (file != null && file == position.file) {
            file.name to (position.line + 1)
          }
          else null
        }.executeSynchronously()
        if (sourceInfo != null) {
          val [fileName, lineNumber] = sourceInfo
          return getLocationsOfLine(type, getSourceName(fileName, type), relativePath, lineNumber)
        }
      }
    }
    catch (_: ObjectCollectedException) {
    }
    catch (_: ClassNotPreparedException) {
    }
    catch (_: AbsentInformationException) {
    }
    catch (_: InternalError) {
      myDebugProcess.printToConsole(
        JavaDebuggerBundle.message("internal.error.locations.of.line", type.name())
      )
    }
    return null
  }

  // Finds exact server file name (from available in type)
  // This is needed because some servers (e.g. WebSphere) put not exact file name such as 'A.jsp  '
  @Throws(AbsentInformationException::class)
  private fun getSourceName(name: String, type: ReferenceType): String {
    return type.sourceNames(stratumId).firstOrNull { it.contains(name) } ?: name
  }

  @Throws(AbsentInformationException::class)
  protected open fun getRelativeSourePathsByType(type: ReferenceType): @Unmodifiable List<String?> {
    return type.sourcePaths(stratumId).map(::getRelativePath)
  }

  @Throws(AbsentInformationException::class)
  protected open fun getLocationsOfLine(
    type: ReferenceType, fileName: String?,
    relativePath: String?, lineNumber: Int,
  ): List<Location>? {
    return type.locationsOfLine(this.stratumId, fileName, lineNumber)
  }

  @RequiresBackgroundThread
  @Throws(NoDataException::class)
  override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? {
    checkSourcePositionFileType(position)

    return myDebugProcess.getRequestsManager().createClassPrepareRequest(ClassPrepareRequestor { debuggerProcess, referenceType ->
      onClassPrepare(debuggerProcess, referenceType, position, requestor)
    }, GENERATED_CLASS_PATTERN)
  }

  protected open fun onClassPrepare(
    debuggerProcess: DebugProcess?, referenceType: ReferenceType,
    position: SourcePosition, requestor: ClassPrepareRequestor,
  ) {
    try {
      if (locationsOfClassAt(referenceType, position) != null) {
        requestor.processClassPrepare(debuggerProcess, referenceType)
      }
    }
    catch (_: NoDataException) {
    }
  }

  protected open fun getRelativePath(sourcePath: String?): String? {
    var sourcePath = sourcePath
    if (sourcePath != null) {
      sourcePath = sourcePath.trim { it <= ' ' }
      val generatedClassesPackage = getGeneratedClassesPackage()
      val prefix = generatedClassesPackage.replace('.', File.separatorChar)

      if (sourcePath.startsWith(prefix)) {
        sourcePath = sourcePath.substring(prefix.length)
        if (sourcePath.startsWith(File.separator)) {
          sourcePath = sourcePath.substring(1)
        }
      }
    }

    return sourcePath
  }

  private companion object {
    private val LOG = Logger.getInstance(JSR45PositionManager::class.java)
  }
}
