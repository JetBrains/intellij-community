// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.jetbrains.bazel.jvm.jps.OutputSink
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.BinaryContent
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.kotlin.backend.common.output.OutputFile
import java.io.File
import java.nio.file.Path

internal class BazelTargetBuildOutputConsumer(
  @JvmField val dataManager: BazelBuildDataProvider?,
  @JvmField val outputSink: OutputSink,
) : OutputConsumer {
  private var registeredSourceCount = 0

  //private val fileGeneratedEvent = FileGeneratedEvent(target)

  private val classes = HashMap<String, CompiledClass>()

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

  fun registerKotlincOutput(context: CompileContext, outputs: List<OutputFile>) {
    val successfullyCompiled = ObjectLinkedOpenHashSet<File>(outputs.size)
    outputSink.registerKotlincOutput(outputs)
    for (fileObject in outputs) {
      val sourceFiles = fileObject.sourceFiles
      if (fileObject.relativePath.endsWith(".kt")) {
        successfullyCompiled.addAll(sourceFiles)
      }

      for (sourceFile in sourceFiles) {
        dataManager?.sourceToOutputMapping?.appendRawRelativeOutput(sourceFile.toPath(), fileObject.relativePath)
      }
    }
    registeredSourceCount += successfullyCompiled.size
    JavaBuilderUtil.registerSuccessfullyCompiled(context, successfullyCompiled)
  }

  fun addRegisteredSourceCount(add: Int) {
    registeredSourceCount += add
  }

  fun registerJavacCompiledClass(
    relativeOutputPath: String,
    sourceFile: Path,
    compiled: CompiledClass?,
  ) {
    compiled?.className?.let {
      classes.put(it, compiled)
    }

    //val previousBuilder = outputToBuilderNameMap.put(outputFile, builderName)
    //if (previousBuilder != null && previousBuilder != builderName) {
    //  val source = sourceFiles.firstOrNull()?.toString()
    //  context.processMessage(CompilerMessage(
    //    builderName, BuildMessage.Kind.ERROR, "Output file \"${outputFile}\" has already been registered by \"$previousBuilder\"", source
    //  ))
    //}

    //fileGeneratedEvent.add("", relativeOutputPath)
    dataManager?.sourceToOutputMapping?.appendRawRelativeOutput(sourceFile, relativeOutputPath)
  }

  override fun registerOutputFile(target: BuildTarget<*>, outputFile: File, sourcePaths: Collection<String>) {
    throw IllegalStateException("")
  }

  fun fireFileGeneratedEvents() {
    //if (!fileGeneratedEvent.isEmpty) {
    //  context.processMessage(fileGeneratedEvent)
    //}
  }

  fun getNumberOfProcessedSources(): Int = registeredSourceCount

  fun clear() {
    classes.clear()
  }

  //private fun addEventsRecursively(output: File, outputRootPath: String?, relativePath: String) {
  //  val children = output.listFiles()
  //  if (children == null) {
  //    fileGeneratedEvent.add(outputRootPath, relativePath)
  //  }
  //  else {
  //    val prefix = if (relativePath.isEmpty() || relativePath == ".") "" else "$relativePath/"
  //    for (child in children) {
  //      addEventsRecursively(child, outputRootPath, prefix + child.getName())
  //    }
  //  }
  //}
}