// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.core

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.BinaryContent
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.build.GeneratedFile
import org.jetbrains.kotlin.build.GeneratedJvmClass
import java.io.File
import java.nio.file.Path

class BazelTargetBuildOutputConsumer(
  @JvmField val dataManager: BazelBuildDataProvider?,
  @JvmField val outputSink: OutputSink,
) : ModuleLevelBuilder.OutputConsumer {
  private var registeredSourceCount = 0
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
    val sourceToOutputMapping = dataManager?.sourceToOutputMapping
    for (fileObject in outputs) {
      val sourceFiles = fileObject.sourceFiles
      val relativePath = fileObject.relativePath.replace(File.separatorChar, '/')
      if (relativePath.endsWith(".class")) {
        successfullyCompiled.addAll(sourceFiles)
      }

      for (sourceFile in sourceFiles) {
        sourceToOutputMapping?.appendRawRelativeOutput(sourceFile.toPath(), relativePath)
      }
    }
    registeredSourceCount += successfullyCompiled.size
    JavaBuilderUtil.registerSuccessfullyCompiled(context, successfullyCompiled)
  }

  // avoid calling asByteArray several times - it is a call to ClassWriter (not some cached data)
  fun registerIncrementalKotlincOutput(context: CompileContext, outputs: List<GeneratedFile>) {
    val successfullyCompiled = ObjectLinkedOpenHashSet<File>(outputs.size)
    outputSink.registerIncrementalKotlincOutput(outputs)
    val sourceToOutputMapping = dataManager?.sourceToOutputMapping
    for (fileObject in outputs) {
      val sourceFiles = fileObject.sourceFiles
      if (fileObject is GeneratedJvmClass) {
        successfullyCompiled.addAll(sourceFiles)
      }

      for (sourceFile in sourceFiles) {
        sourceToOutputMapping?.appendRawRelativeOutput(sourceFile.toPath(), fileObject.relativePath)
      }
    }
    registeredSourceCount += successfullyCompiled.size
    JavaBuilderUtil.registerSuccessfullyCompiled(context, successfullyCompiled)
  }

  fun registerKotlincAbiOutput(outputs: List<OutputFile>) {
    outputSink.registerKotlincAbiOutput(outputs)
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

    dataManager?.sourceToOutputMapping?.appendRawRelativeOutput(sourceFile, relativeOutputPath)
  }

  override fun registerOutputFile(target: BuildTarget<*>, outputFile: File, sourcePaths: Collection<String>) {
    throw IllegalStateException("")
  }

  fun getNumberOfProcessedSources(): Int = registeredSourceCount

  fun clear() {
    classes.clear()
  }
}