// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.TestLoggerFactory
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.TracerManager
import org.jetbrains.intellij.build.impl.TracerProviderManager
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val initializeTracer by lazy {
  val endpoint = System.getenv("JAEGER_ENDPOINT")
  if (endpoint != null) {
    val defaultExporters = TracerProviderManager.getSpanExporterProvider().get()
    TracerProviderManager.setSpanExporterProvider {
      defaultExporters + JaegerGrpcSpanExporter.builder().setEndpoint(endpoint).build()
    }
  }
}

fun createBuildContext(homePath: String, productProperties: ProductProperties,
                       buildTools: ProprietaryBuildTools,
                       skipDependencySetup: Boolean = false,
                       communityHomePath: String = "$homePath/community",
                       buildOptionsCustomizer: (BuildOptions) -> Unit = {}): BuildContext {
  val options = BuildOptions()
  options.isSkipDependencySetup = skipDependencySetup
  options.isIsTestBuild = true
  options.buildStepsToSkip.add(BuildOptions.getTEAMCITY_ARTIFACTS_PUBLICATION())
  options.outputRootPath = FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).absolutePath
  options.isUseCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
  buildOptionsCustomizer(options)
  return BuildContext.createContext(communityHomePath, homePath, productProperties, buildTools, options)
}

fun runTestBuild(homePath: String,
                 productProperties: ProductProperties,
                 buildTools: ProprietaryBuildTools,
                 communityHomePath: String = "$homePath/community",
                 traceSpanName: String? = null,
                 verifier: (paths: BuildPaths) -> Unit = {},
                 buildOptionsCustomizer: (BuildOptions) -> Unit = {}) {
  initializeTracer

  // to see in Jaeger as a one trace
  val traceFileName = "${productProperties.baseFileName}-trace.json"
  val span = TracerManager.spanBuilder(traceSpanName ?: "test build of ${productProperties.baseFileName}").startSpan()
  var spanEnded = false
  val spanScope = span.makeCurrent()

  try {
    val buildContext = createBuildContext(homePath, productProperties, buildTools, false, communityHomePath, buildOptionsCustomizer)
    val outDir = buildContext.paths.buildOutputDir
    span.setAttribute("outDir", outDir.toString())
    val messages = buildContext.messages as BuildMessagesImpl
    try {
      BuildTasks.create(buildContext).runTestBuild()
      verifier(buildContext.paths)
    }
    catch (e: Throwable) {
      if (e !is FileComparisonFailure) {
        span.recordException(e)
      }
      span.setStatus(StatusCode.ERROR)

      copyDebugLog(productProperties, messages)

      throw e
    }
    finally {
      // redirect debug logging to some other file to prevent locking of output directory on Windows
      val newDebugLog = FileUtil.createTempFile("debug-log-", ".log", true)
      messages.setDebugLogPath(newDebugLog.toPath())

      spanScope.close()
      span.end()
      spanEnded = true
      copyPerfReport(traceFileName)

      try {
        NioFiles.deleteRecursively(outDir)
      }
      catch (e: Throwable) {
        System.err.println("cannot cleanup $outDir:")
        e.printStackTrace(System.err)
      }
    }
  }
  finally {
    if (!spanEnded) {
      spanScope.close()
      span.end()
    }
  }
}

private fun copyDebugLog(productProperties: ProductProperties, messages: BuildMessagesImpl) {
  try {
    val targetFile = Path.of(TestLoggerFactory.getTestLogDir(), "${productProperties.baseFileName}-test-build-debug.log")
    Files.createDirectories(targetFile.parent)
    Files.copy(messages.debugLogFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
    messages.info("Debug log copied to $targetFile")
  }
  catch (e: Throwable) {
    messages.warning("Failed to copy debug log: ${e.message}")
  }
}

private fun copyPerfReport(traceFileName: String) {
  val targetFile = Path.of(TestLoggerFactory.getTestLogDir(), traceFileName)
  Files.createDirectories(targetFile.parent)
  val file = TracerManager.finish() ?: return
  try {
    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
    println("Performance report is written to $targetFile")
  }
  catch (ignore: NoSuchFileException) {
  }
  catch (e: Throwable) {
    System.err.println("cannot write performance report: ${e.message}")
  }
}