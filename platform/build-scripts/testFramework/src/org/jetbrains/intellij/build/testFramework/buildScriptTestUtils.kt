// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.ExceptionUtil
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.TracerManager
import org.jetbrains.intellij.build.impl.TracerProviderManager
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.junit.AssumptionViolatedException
import java.net.http.HttpConnectTimeoutException
import org.jetbrains.intellij.build.testFramework.binaryReproducibility.BuildArtifactsReproducibilityTest
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

fun customizeBuildOptionsForTest(options: BuildOptions, productProperties: ProductProperties, skipDependencySetup: Boolean = false) {
  options.isSkipDependencySetup = skipDependencySetup
  options.isIsTestBuild = true
  options.buildStepsToSkip.addAll(listOf(
    BuildOptions.getTEAMCITY_ARTIFACTS_PUBLICATION(),
    BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP,
    BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP,
    BuildOptions.WIN_SIGN_STEP,
    BuildOptions.MAC_SIGN_STEP,
  ))
  options.buildDmgWithBundledJre = false
  options.buildDmgWithoutBundledJre = false
  options.buildUnixSnaps = false
  options.outputRootPath = FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).absolutePath
  options.isUseCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
}

fun createBuildContext(
  homePath: String, productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  skipDependencySetup: Boolean = false,
  communityHomePath: String = "$homePath/community",
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
): BuildContext {
  val options = BuildOptions()
  customizeBuildOptionsForTest(options, productProperties, skipDependencySetup)
  buildOptionsCustomizer(options)
  return BuildContext.createContext(communityHomePath, homePath, productProperties, buildTools, options)
}

fun runTestBuild(
  homePath: String,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  communityHomePath: String = "$homePath/community",
  traceSpanName: String? = null,
  onFinish: (context: BuildContext) -> Unit = {},
  buildOptionsCustomizer: (BuildOptions) -> Unit = {}
) {
  val buildArtifactsReproducibilityTest = BuildArtifactsReproducibilityTest()
  if (!buildArtifactsReproducibilityTest.isEnabled) {
    testBuild(homePath, productProperties, buildTools, communityHomePath, traceSpanName, onFinish, buildOptionsCustomizer)
  }
  else {
    testBuild(homePath, productProperties, buildTools, communityHomePath, traceSpanName, buildOptionsCustomizer = {
      buildOptionsCustomizer(it)
      buildArtifactsReproducibilityTest.configure(it)
    }, onFinish = { firstIteration ->
      onFinish(firstIteration)
      testBuild(homePath, productProperties, buildTools, communityHomePath, traceSpanName, buildOptionsCustomizer = {
        buildOptionsCustomizer(it)
        buildArtifactsReproducibilityTest.configure(it)
      }, onFinish = { nextIteration ->
        onFinish(nextIteration)
        buildArtifactsReproducibilityTest.compare(firstIteration, nextIteration)
      })
    })
  }
}

private fun testBuild(
  homePath: String,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools,
  communityHomePath: String,
  traceSpanName: String?,
  onFinish: (context: BuildContext) -> Unit,
  buildOptionsCustomizer: (BuildOptions) -> Unit,
) {
  val buildContext = createBuildContext(
    homePath = homePath,
    productProperties = productProperties,
    buildTools = buildTools,
    skipDependencySetup = false,
    communityHomePath = communityHomePath,
    buildOptionsCustomizer = buildOptionsCustomizer,
  )

  runTestBuild(
    buildContext = buildContext,
    traceSpanName = traceSpanName,
    onFinish = onFinish,
  )
}

// FIXME: test reproducibility
fun runTestBuild(
  buildContext: BuildContext,
  traceSpanName: String? = null,
  onFinish: (context: BuildContext) -> Unit = {},
) {
  initializeTracer

  val productProperties = buildContext.productProperties

  // to see in Jaeger as a one trace
  val traceFileName = "${productProperties.baseFileName}-trace.json"
  val span = TracerManager.spanBuilder(traceSpanName ?: "test build of ${productProperties.baseFileName}").startSpan()
  var spanEnded = false
  val spanScope = span.makeCurrent()

  try {
    val outDir = buildContext.paths.buildOutputDir
    span.setAttribute("outDir", outDir.toString())
    val messages = buildContext.messages as BuildMessagesImpl
    try {
      BuildTasks.create(buildContext).runTestBuild()
      onFinish(buildContext)
    }
    catch (e: Throwable) {
      if (e !is FileComparisonFailure) {
        span.recordException(e)
      }
      span.setStatus(StatusCode.ERROR)

      copyDebugLog(productProperties, messages)

      if (ExceptionUtil.causedBy(e, HttpConnectTimeoutException::class.java)) {
        //todo use com.intellij.platform.testFramework.io.ExternalResourcesChecker after next update of jps-bootstrap library
        throw AssumptionViolatedException("failed to load data for build scripts", e)
      }
      else {
        throw e
      }
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