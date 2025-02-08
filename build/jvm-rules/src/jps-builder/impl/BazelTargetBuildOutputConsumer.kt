// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import org.jetbrains.bazel.jvm.jps.OutputSink
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

internal class BazelTargetBuildOutputConsumer(
  private val context: CompileContext,
  target: ModuleBuildTarget,
  dataManager: BazelBuildDataProvider?,
  @JvmField val outputSink: OutputSink,
) : OutputConsumer {
  private val outputConsumer = BazelBuildOutputConsumer(target, context, dataManager)
  private val classes = HashMap<String, CompiledClass>()
  private val outputToBuilderNameMap = Collections.synchronizedMap(HashMap<File, String>())

  @Volatile
  private var currentBuilderName: String? = null

  fun setCurrentBuilderName(builderName: String?) {
    currentBuilderName = builderName
  }

  override fun getTargetCompiledClasses(target: BuildTarget<*>): Collection<CompiledClass> {
    throw IllegalStateException("getTargetCompiledClasses is not and will be not supported")
  }

  override fun getCompiledClasses(): Map<String, CompiledClass> = classes

  override fun lookupClassBytes(className: String?): BinaryContent? = classes.get(className)?.content

  override fun registerCompiledClass(target: BuildTarget<*>?, compiled: CompiledClass) {
    val className = compiled.className
    if (className != null) {
      classes.put(className, compiled)
    }
    if (target != null) {
      registerOutputFile(target = target, outputFile = compiled.outputFile, sourcePaths = compiled.sourceFilesPaths)
    }
  }

  fun registerCompiledClass(
    relativeOutputPath: String,
    outputFile: File,
    sourceFiles: Collection<Path>,
    compiled: CompiledClass,
    builderName: String,
  ) {
    compiled.className?.let {
      classes.put(it, compiled)
    }

    val previousBuilder = outputToBuilderNameMap.put(outputFile, builderName)
    if (previousBuilder != null && previousBuilder != builderName) {
      val source = sourceFiles.firstOrNull()?.toString()
      context.processMessage(CompilerMessage(
        builderName, BuildMessage.Kind.ERROR, "Output file \"${outputFile}\" has already been registered by \"$previousBuilder\"", source
      ))
    }

    outputConsumer.fileGeneratedEvent.add(outputConsumer.targetOutDirPath, relativeOutputPath)
    outputConsumer.registeredSources.addAll(sourceFiles)
    if (outputConsumer.dataManager != null) {
      for (source in sourceFiles) {
        outputConsumer.dataManager.sourceToOutputMapping.appendRawRelativeOutput(source, relativeOutputPath)
      }
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
    outputToBuilderNameMap.clear()
  }
}

private class BazelBuildOutputConsumer(
  target: BuildTarget<*>,
  private val context: CompileContext,
  @JvmField val dataManager: BazelBuildDataProvider?,
) {
  @JvmField val fileGeneratedEvent = FileGeneratedEvent(target)

  @JvmField val targetOutDir = (target as BazelModuleBuildTarget).outDir
  @JvmField val targetOutDirPath = targetOutDir.invariantSeparatorsPathString

  @JvmField val registeredSources = hashSet<Path>()

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
    if (dataManager != null) {
      for (sourcePath in sourcePaths) {
        dataManager.sourceToOutputMapping.appendRawRelativeOutput(sourcePath, relativePath)
      }
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