// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.bazel.jvm.jps.impl

import org.jetbrains.bazel.jvm.jps.hashMap
import org.jetbrains.bazel.jvm.jps.hashSet
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.BinaryContent
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent
import java.io.File
import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.text.startsWith

internal class ChunkBuildOutputConsumerImpl(
  private val context: CompileContext,
  target: ModuleBuildTarget,
  dataManager: BazelBuildDataProvider,
) : OutputConsumer {
  private val outputConsumer = BazelBuildOutputConsumer(target, context, dataManager)
  private val classes = hashMap<String, CompiledClass>()
  private val classesMap = ArrayList<CompiledClass>()
  private val outputToBuilderNameMap = Collections.synchronizedMap(hashMap<File, String>())

  @Volatile
  private var currentBuilderName: String? = null

  fun setCurrentBuilderName(builderName: String?) {
    currentBuilderName = builderName
  }

  override fun getTargetCompiledClasses(target: BuildTarget<*>): Collection<CompiledClass> = classesMap

  override fun getCompiledClasses(): Map<String, CompiledClass> = classes

  override fun lookupClassBytes(className: String?): BinaryContent? = classes.get(className)?.content

  override fun registerCompiledClass(target: BuildTarget<*>?, compiled: CompiledClass) {
    val className = compiled.className
    if (className != null) {
      classes.put(className, compiled)
      if (target != null) {
        classesMap.add(compiled)
      }
    }
    if (target != null) {
      registerOutputFile(target = target, outputFile = compiled.outputFile, sourcePaths = compiled.sourceFilesPaths)
    }
  }

  override fun registerOutputFile(target: BuildTarget<*>, outputFile: File, sourcePaths: Collection<String>) {
    val currentBuilder = currentBuilderName
    if (currentBuilder != null) {
      val previousBuilder = outputToBuilderNameMap.put(outputFile, currentBuilder)
      if (previousBuilder != null && previousBuilder != currentBuilder) {
        val source = if (sourcePaths.isEmpty()) null else sourcePaths.iterator().next()
        context.processMessage(CompilerMessage(
          currentBuilder, BuildMessage.Kind.ERROR, "Output file \"${outputFile}\" has already been registered by \"$previousBuilder\"", source
        ))
      }
    }
    outputConsumer.registerOutputFile(outputFile = outputFile.toPath(), sourcePaths = sourcePaths.map { Path.of(it) })
  }

  fun fireFileGeneratedEvents() {
    outputConsumer.fireFileGeneratedEvent()
  }

  fun getNumberOfProcessedSources(): Int = outputConsumer.numberOfProcessedSources

  fun clear() {
    classes.clear()
    classesMap.clear()
    outputToBuilderNameMap.clear()
  }
}

private class BazelBuildOutputConsumer(
  target: BuildTarget<*>,
  private val context: CompileContext,
  private val dataManager: BazelBuildDataProvider,
) {
  private val fileGeneratedEvent = FileGeneratedEvent(target)

  private val targetOutDir = (target as BazelModuleBuildTarget).outDir
  private val targetOutDirPath = targetOutDir.invariantSeparatorsPathString

  private val registeredSources = hashSet<Path>()

  private fun addEventsRecursively(output: File, outputRootPath: String?, relativePath: String) {
    val children = output.listFiles()
    if (children == null) {
      fileGeneratedEvent.add(outputRootPath, relativePath)
    }
    else {
      val prefix = if (relativePath.isEmpty() || relativePath == ".") "" else "$relativePath/"
      for (child in children) {
        addEventsRecursively(child, outputRootPath, prefix + child.getName())
      }
    }
  }

  fun registerOutputFile(outputFile: Path, sourcePaths: Collection<Path>) {
    val relativePath = targetOutDir.relativize(outputFile).invariantSeparatorsPathString
    require(!relativePath.startsWith("../")) {
      "$outputFile must be created under $targetOutDir"
    }
    fileGeneratedEvent.add(targetOutDirPath, relativePath)
    registeredSources.addAll(sourcePaths)
    for (sourcePath in sourcePaths) {
      dataManager.sourceToOutputMapping.appendRawRelativeOutput(sourcePath, relativePath)
    }
  }

  val numberOfProcessedSources: Int
    get() = registeredSources.size

  fun fireFileGeneratedEvent() {
    if (!fileGeneratedEvent.isEmpty) {
      context.processMessage(fileGeneratedEvent)
    }
  }
}