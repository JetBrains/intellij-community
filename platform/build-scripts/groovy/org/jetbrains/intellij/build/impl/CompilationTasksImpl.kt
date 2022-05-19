// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import com.intellij.util.lang.JavaVersion
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.compilation.CompilationPartsUtil
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache
import java.nio.file.Files
import java.nio.file.Path

class CompilationTasksImpl(private val context: CompilationContext) : CompilationTasks {
  private val jpsCache by lazy { PortableCompilationCache(context) }

  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    reuseCompiledClassesIfProvided()
    val options = context.options
    val messages = context.messages
    if (options.useCompiledClassesFromProjectOutput) {
      messages.info("Compilation skipped, the compiled classes from \'${context.projectOutputDirectory}\' will be used")
      resolveProjectDependencies()
    }
    else if (jpsCache.canBeUsed) {
      messages.info("JPS remote cache will be used")
      jpsCache.downloadCacheAndCompileProject()
    }
    else if (options.pathToCompiledClassesArchive != null) {
      messages.info("Compilation skipped, the compiled classes from \'${options.pathToCompiledClassesArchive}\' will be used")
      resolveProjectDependencies()
    }
    else if (options.pathToCompiledClassesArchivesMetadata != null) {
      messages.info("Compilation skipped, the compiled classes from \'${options.pathToCompiledClassesArchivesMetadata}\' will be used")
      resolveProjectDependencies()
    }
    else {
      if (!JavaVersion.current().isAtLeast(11)) {
        messages.error("Build script must be executed under Java 11 to compile intellij project, " +
                       "but it\'s executed under Java ${JavaVersion.current()}")
      }
      resolveProjectDependencies()
      messages.progress("Compiling project")
      val runner = JpsCompilationRunner(context)
      try {
        if (moduleNames == null) {
          if (includingTestsInModules == null) {
            runner.buildAll()
          }
          else {
            runner.buildProduction()
          }
        }
        else {
          val invalidModules = moduleNames.filter { context.findModule(it) == null }
          if (!invalidModules.isEmpty()) {
            messages.warning("The following modules won\'t be compiled: $invalidModules")
          }
          runner.buildModules(moduleNames.mapNotNull(context::findModule))
        }
        if (includingTestsInModules != null) {
          for (moduleName in includingTestsInModules) {
            runner.buildModuleTests(context.findRequiredModule(moduleName))
          }
        }
      }
      catch (e: Throwable) {
        messages.error("Compilation failed with exception: $e", e)
      }
    }
  }

  override fun buildProjectArtifacts(artifactNames: Set<String>) {
    if (artifactNames.isEmpty()) {
      return
    }

    try {
      var buildIncludedModules = !areCompiledClassesProvided(context.options)
      if (buildIncludedModules && jpsCache.canBeUsed) {
        jpsCache.downloadCacheAndCompileProject()
        buildIncludedModules = false
      }
      JpsCompilationRunner(context).buildArtifacts(artifactNames, buildIncludedModules)
    }
    catch (e: Throwable) {
      context.messages.error("Building project artifacts failed with exception: $e", e)
    }
  }

  override fun resolveProjectDependencies() {
    JpsCompilationRunner(context).resolveProjectDependencies()
  }

  override fun compileAllModulesAndTests() {
    compileModules(moduleNames = null, includingTestsInModules = null)
  }

  override fun resolveProjectDependenciesAndCompileAll() {
    resolveProjectDependencies()
    context.compilationData.statisticsReported = false
    compileAllModulesAndTests()
  }

  @Synchronized
  override fun reuseCompiledClassesIfProvided() {
    if (context.compilationData.compiledClassesAreLoaded) {
      return
    }

    if (context.options.cleanOutputFolder) {
      cleanOutput(context)
    }
    else {
      context.messages.info("cleanOutput step was skipped")
    }

    if (context.options.useCompiledClassesFromProjectOutput) {
      context.messages.info("Compiled classes reused from \'${context.projectOutputDirectory}\'")
    }
    else if (context.options.pathToCompiledClassesArchivesMetadata != null) {
      CompilationPartsUtil.fetchAndUnpackCompiledClasses(context.messages, context.projectOutputDirectory, context.options)
    }
    else if (context.options.pathToCompiledClassesArchive != null) {
      unpackCompiledClasses(context.projectOutputDirectory.toPath(), context)
    }
    else if (jpsCache.canBeUsed && !jpsCache.isCompilationRequired()) {
      jpsCache.downloadCacheAndCompileProject()
    }
    context.compilationData.compiledClassesAreLoaded = true
  }
}

private fun cleanOutput(context: CompilationContext) {
  val outputDirectoriesToKeep = HashSet<String>(5)
  outputDirectoriesToKeep.add("log")
  if (areCompiledClassesProvided(context.options)) {
    outputDirectoriesToKeep.add("classes")
  }
  if (context.options.incrementalCompilation) {
    outputDirectoriesToKeep.add(context.compilationData.dataStorageRoot.name)
    outputDirectoriesToKeep.add("classes")
    outputDirectoriesToKeep.add("project-artifacts")
  }

  val outDir = context.paths.buildOutputDir
  spanBuilder("clean output")
    .setAttribute("path", outDir.toString())
    .setAttribute(AttributeKey.stringArrayKey("outputDirectoriesToKeep"), java.util.List.copyOf(outputDirectoriesToKeep))
    .use { span ->
      Files.newDirectoryStream(outDir).use { dirStream ->
        for (file in dirStream) {
          val attributes = Attributes.of(AttributeKey.stringKey("dir"), outDir.relativize(file).toString())
          if (outputDirectoriesToKeep.contains(file.fileName.toString())) {
            span.addEvent("skip cleaning", attributes)
          }
          else {
            span.addEvent("delete", attributes)
            NioFiles.deleteRecursively(file)
          }
        }
      }
      null
    }
}

internal fun areCompiledClassesProvided(options: BuildOptions): Boolean {
  return options.useCompiledClassesFromProjectOutput ||
         options.pathToCompiledClassesArchive != null ||
         options.pathToCompiledClassesArchivesMetadata != null
}

private fun unpackCompiledClasses(classOutput: Path, context: CompilationContext) {
  spanBuilder("unpack compiled classes archive").use {
    NioFiles.deleteRecursively(classOutput)
    Decompressor.Zip(context.options.pathToCompiledClassesArchive ?: throw IllegalStateException("intellij.build.compiled.classes.archive is not set")).extract(classOutput)
  }
}