// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import com.intellij.util.lang.JavaVersion
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import java.nio.file.Path

internal object CompiledClasses {
  fun checkOptions(context: CompilationContext) {
    val options = context.options
    val messages = context.messages
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                       "so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && PortableCompilationCache.IS_ENABLED) {
      messages.warning("JPS Cache is enabled so the archive with compiled project output won't be used")
      options.pathToCompiledClassesArchive = null
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && PortableCompilationCache.IS_ENABLED) {
      messages.warning("JPS Cache is enabled " +
                       "so the archive with the compiled project output metadata won't be used to fetch compile output")
      options.pathToCompiledClassesArchivesMetadata = null
    }
    if (options.pathToCompiledClassesArchive != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                       "so the archive with compiled project output won't be used")
      options.pathToCompiledClassesArchive = null
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output metadata is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                       "so the archive with the compiled project output metadata won't be used to fetch compile output")
      options.pathToCompiledClassesArchivesMetadata = null
    }
    if (!options.useCompiledClassesFromProjectOutput) {
      messages.info("Incremental compilation: ${options.incrementalCompilation}")
    }
  }

  /**
   * @return true even if [PortableCompilationCache.IS_ENABLED] because incremental compilation
   * may still be triggered due to [PortableCompilationCache.isCompilationRequired]
   */
  fun isCompilationRequired(options: BuildOptions): Boolean {
    return !options.useCompiledClassesFromProjectOutput &&
           options.pathToCompiledClassesArchive == null &&
           options.pathToCompiledClassesArchivesMetadata == null
  }

  fun keepCompilationState(options: BuildOptions): Boolean {
    return PortableCompilationCache.IS_ENABLED ||
           options.useCompiledClassesFromProjectOutput ||
           options.pathToCompiledClassesArchive == null ||
           options.pathToCompiledClassesArchivesMetadata != null ||
           options.incrementalCompilation
  }

  @Synchronized
  fun reuseOrCompile(context: CompilationContext, moduleNames: Collection<String>? = null, includingTestsInModules: List<String>? = null) {
    val span = Span.current()
    when {
      context.options.useCompiledClassesFromProjectOutput -> {
        span.addEvent("compiled classes reused", Attributes.of(
          AttributeKey.stringKey("dir"), context.classesOutputDirectory.toString(),
        ))
      }
      PortableCompilationCache.IS_ENABLED -> {
        span.addEvent("JPS remote cache will be used for compilation")
        PortableCompilationCache(context).downloadCacheAndCompileProject()
      }
      context.options.pathToCompiledClassesArchive != null -> {
        span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"),
                                                           context.options.pathToCompiledClassesArchive.toString()))
        unpackCompiledClasses(classOutput = context.classesOutputDirectory, context = context)
      }
      context.options.pathToCompiledClassesArchivesMetadata != null -> {
        span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"),
                                                           context.options.pathToCompiledClassesArchive.toString()))
        val forInstallers = System.getProperty("intellij.fetch.compiled.classes.for.installers", "false").toBoolean()
        fetchAndUnpackCompiledClasses(
          reportStatisticValue = context.messages::reportStatisticValue,
          withScope = { name, operation -> context.messages.block(name, operation) },
          classOutput = context.classesOutputDirectory,
          metadataFile = Path.of(context.options.pathToCompiledClassesArchivesMetadata!!),
          saveHash = !forInstallers,
        )
      }
      else -> {
        if (context.options.incrementalCompilation) {
          span.addEvent("reusing locally available compilation state if any")
        }
        else {
          span.addEvent("no compiled classes can be reused")
        }
        compileLocally(context, moduleNames, includingTestsInModules)
        return
      }
    }
    context.options.useCompiledClassesFromProjectOutput = true
  }

  private fun unpackCompiledClasses(classOutput: Path, context: CompilationContext) {
    context.messages.block("unpack compiled classes archive") {
      NioFiles.deleteRecursively(classOutput)
      Decompressor.Zip(context.options.pathToCompiledClassesArchive ?: error("intellij.build.compiled.classes.archive is not set"))
        .extract(classOutput)
    }
  }

  private fun compileLocally(context: CompilationContext,
                             moduleNames: Collection<String>? = null,
                             includingTestsInModules: List<String>? = null) {
    require(JavaVersion.current().isAtLeast(17)) {
      "Build script must be executed under Java 17 to compile intellij project but it's executed under Java ${JavaVersion.current()}"
    }
    context.messages.progress("Compiling project")
    context.compilationData.statisticsReported = false
    val runner = JpsCompilationRunner(context)
    try {
      runner.compile(context, moduleNames, includingTestsInModules)
    }
    catch (e: Exception) {
      if (!context.options.incrementalCompilation) {
        throw e
      }
      context.messages.warning("Incremental compilation failed. Re-trying with clean build.")
      context.options.incrementalCompilation = false
      context.compilationData.reset()
      runner.compile(context, moduleNames, includingTestsInModules)
      val successMessage = "Compilation successful after clean build retry"
      context.messages.info(successMessage)
      println("##teamcity[buildStatus status='SUCCESS' text='$successMessage']")
      context.messages.reportStatisticValue("Incremental compilation failures", "1")
    }
  }

  private fun JpsCompilationRunner.compile(context: CompilationContext,
                                           moduleNames: Collection<String>?,
                                           includingTestsInModules: List<String>?) {
    when {
      moduleNames != null -> buildModules(moduleNames.map(context::findRequiredModule))
      includingTestsInModules != null -> buildProduction()
      else -> {
        buildAll()
        context.options.useCompiledClassesFromProjectOutput = true
      }
    }
    context.options.incrementalCompilation = true
    includingTestsInModules?.forEach {
      buildModuleTests(context.findRequiredModule(it))
    }
  }
}