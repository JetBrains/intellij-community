// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework

import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.rt.execution.junit.FileComparisonData
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.ExceptionUtilRt
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.testFramework.binaryReproducibility.BuildArtifactsReproducibilityTest
import org.opentest4j.TestAbortedException
import java.net.http.HttpConnectTimeoutException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name

fun customizeBuildOptionsForTest(options: BuildOptions, productProperties: ProductProperties, skipDependencySetup: Boolean = false) {
  options.skipDependencySetup = skipDependencySetup
  options.isTestBuild = true
  options.buildStepsToSkip.addAll(listOf(
    BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP,
    BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP,
    BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP,
    BuildOptions.WIN_SIGN_STEP,
    BuildOptions.MAC_SIGN_STEP,
  ))
  options.buildMacArtifactsWithRuntime = false
  options.buildMacArtifactsWithoutRuntime = false
  options.buildUnixSnaps = false
  options.outputRootPath = FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).toPath()
  options.useCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
}

suspend fun createBuildContext(
  homePath: Path, productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  communityHomePath: BuildDependenciesCommunityRoot,
  skipDependencySetup: Boolean = false,
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
): BuildContext {
  val options = BuildOptions()
  customizeBuildOptionsForTest(options, productProperties, skipDependencySetup)
  buildOptionsCustomizer(options)
  return BuildContextImpl.createContext(communityHome = communityHomePath,
                                        projectHome = homePath,
                                        productProperties = productProperties,
                                        proprietaryBuildTools = buildTools,
                                        options = options)
}

// don't expose BuildDependenciesCommunityRoot
fun runTestBuild(homePath: Path, productProperties: ProductProperties, buildTools: ProprietaryBuildTools, buildOptionsCustomizer: (BuildOptions) -> Unit = {}) {
  runTestBuild(homePath = homePath, productProperties = productProperties, buildTools = buildTools, traceSpanName = null, buildOptionsCustomizer = buildOptionsCustomizer)
}

fun runTestBuild(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  communityHomePath: BuildDependenciesCommunityRoot = BuildDependenciesCommunityRoot(homePath.resolve("community")),
  traceSpanName: String? = null,
  onFinish: suspend (context: BuildContext) -> Unit = {},
  buildOptionsCustomizer: (BuildOptions) -> Unit = {}
) {
  if (!BuildArtifactsReproducibilityTest.isEnabled) {
    testBuild(homePath = homePath,
              productProperties = productProperties,
              buildTools = buildTools,
              communityHomePath = communityHomePath,
              traceSpanName = traceSpanName,
              onFinish = onFinish,
              buildOptionsCustomizer = buildOptionsCustomizer)
    return
  }

  val buildArtifactsReproducibilityTest = BuildArtifactsReproducibilityTest()
  testBuild(homePath = homePath,
            productProperties = productProperties,
            buildTools = buildTools,
            communityHomePath = communityHomePath,
            traceSpanName = traceSpanName,
            buildOptionsCustomizer = {
              buildOptionsCustomizer(it)
              buildArtifactsReproducibilityTest.configure(it)
            },
            onFinish = { firstIteration ->
              onFinish(firstIteration)
              firstIteration.cleanBuildOutput()
              testBuild(homePath = homePath,
                        productProperties = productProperties,
                        buildTools = buildTools,
                        communityHomePath = communityHomePath,
                        traceSpanName = traceSpanName,
                        buildOptionsCustomizer = {
                          buildOptionsCustomizer(it)
                          buildArtifactsReproducibilityTest.configure(it)
                        },
                        onFinish = { nextIteration ->
                          onFinish(nextIteration)
                          nextIteration.cleanBuildOutput()
                          buildArtifactsReproducibilityTest.compare(firstIteration, nextIteration)
                        })
            })
}

private fun BuildContext.cleanBuildOutput() {
  Files.newDirectoryStream(paths.buildOutputDir).use { content ->
    content.filter { it != paths.artifactDir }.forEach(NioFiles::deleteRecursively)
  }
  Files.newDirectoryStream(paths.artifactDir).use { content ->
    content.filter { it.name == "unscrambled" || it.name == "scramble-logs" }.forEach(NioFiles::deleteRecursively)
  }
}

private fun testBuild(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools,
  communityHomePath: BuildDependenciesCommunityRoot,
  traceSpanName: String?,
  onFinish: suspend (context: BuildContext) -> Unit,
  buildOptionsCustomizer: (BuildOptions) -> Unit,
) {
  runBlocking(Dispatchers.Default) {
    runTestBuild(
      context = createBuildContext(
        homePath = homePath,
        productProperties = productProperties,
        buildTools = buildTools,
        skipDependencySetup = false,
        communityHomePath = communityHomePath,
        buildOptionsCustomizer = buildOptionsCustomizer,
      ),
      traceSpanName = traceSpanName,
      onFinish = onFinish,
    )
  }
}

// FIXME: test reproducibility
suspend fun runTestBuild(context: BuildContext, traceSpanName: String? = null, onFinish: suspend (context: BuildContext) -> Unit = {}) {
  val productProperties = context.productProperties

  // to see in Jaeger as a one trace
  val traceFileName = "${productProperties.baseFileName}-trace.json"
  val outDir = context.paths.buildOutputDir
  var error: Throwable? = null
  try {
    spanBuilder(traceSpanName ?: "test build of ${productProperties.baseFileName}")
      .setAttribute("outDir", outDir.toString())
      .useWithScope2 { span ->
        try {
          buildDistributions(context)
          onFinish(context)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          if (e !is FileComparisonData) {
            span.recordException(e)
          }
          span.setStatus(StatusCode.ERROR)

          copyDebugLog(productProperties, context.messages as BuildMessagesImpl)

          if (ExceptionUtilRt.causedBy(e, HttpConnectTimeoutException::class.java)) {
            //todo use com.intellij.platform.testFramework.io.ExternalResourcesChecker after next update of jps-bootstrap library
            error = TestAbortedException("failed to load data for build scripts", e)
          }
          else {
            error = e
          }
        }
      }
  }
  finally {
    closeKtorClient()

    // close debug logging to prevent locking of output directory on Windows
    (context.messages as BuildMessagesImpl).close()

    copyPerfReport(traceFileName)
    try {
      NioFiles.deleteRecursively(outDir)
    }
    catch (e: Throwable) {
      System.err.println("cannot cleanup $outDir:")
      e.printStackTrace(System.err)
    }
  }

  error?.let {
    throw it
  }
}

private fun copyDebugLog(productProperties: ProductProperties, messages: BuildMessagesImpl) {
  try {
    val targetFile = TestLoggerFactory.getTestLogDir().resolve("${productProperties.baseFileName}-test-build-debug.log")
    Files.createDirectories(targetFile.parent)
    Files.copy(messages.debugLogFile!!, targetFile, StandardCopyOption.REPLACE_EXISTING)
    Span.current().addEvent("debug log copied to $targetFile")
  }
  catch (e: Throwable) {
    Span.current().addEvent("failed to copy debug log: ${e.message}")
  }
}

private fun copyPerfReport(traceFileName: String) {
  val targetFile = TestLoggerFactory.getTestLogDir().resolve(traceFileName)
  Files.createDirectories(targetFile.parent)
  val file = TraceManager.finish() ?: return
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