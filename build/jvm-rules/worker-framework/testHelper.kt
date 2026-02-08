// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.walk
import kotlin.system.exitProcess

enum class TestModules(@JvmField val sourcePaths: List<String>, private val paramsPath: String) {
  UTIL_BASE_KMP(listOf("platform/util/base/kmp/src", "platform/util/base/kmp/srcJvmMain"), "platform/util/base/kmp/util-base-kmp.jar-0.params"),
  UTIL(listOf("platform/util/src"), "platform/util/util.jar-0.params"),
  UTIL_RT(listOf("platform/util-rt/src"), "platform/util-rt/util-rt.jar-0.params"),
  STAT_CONFIG(listOf("platform/statistics/config/src"), "platform/statistics/config/config.jar-0.params"),
  XML_DOM(listOf("platform/util/xmlDom/src"), "platform/util/xmlDom/xmlDom.jar-0.params"),
  PLATFORM_IMPL(listOf("platform/platform-impl/src"), "platform/platform-impl/ide-impl.jar-0.params"),
  RUNTIME_REPOSITORY(listOf("platform/runtime/repository/src"), "platform/runtime/repository/repository.jar-0.params"),
  LANG_IMPL(listOf("platform/lang-impl/src", "platform/lang-impl/gen"), "platform/lang-impl/lang-impl.jar-0.params"),
  PLATFORM_BOOTSTRAP(listOf("platform/platform-impl/bootstrap/src"), "platform/platform-impl/bootstrap/ide-bootstrap.jar-0.params"),
  DEVKIT_CORE(listOf("plugins/devkit/devkit-core/src", "plugins/devkit/devkit-core/gen"), "plugins/devkit/devkit-core/core.jar-0.params"),
  JAVA_PSI_API(listOf("java/java-psi-api"), "java/java-psi-api/psi.jar-0.params"),
  JEWEL(listOf("platform/jewel/foundation/src/main/kotlin"), "platform/jewel/foundation/foundation.jar-0.params"),

  TEST_AM_B(listOf("testData/IJI-2602/b"), "testData/IJI-2602/b.jar-0.params"),
  ;

  fun getParams(testPaths: TestWorkerPaths): String {
    @Suppress("SpellCheckingInspection")
    if (testPaths.communityDir == null) {
      return Files.readString(testPaths.baseDir.resolve("bazel-out/darwin_arm64-fastbuild/bin").resolve(paramsPath)).trim().replace("external/+_", "../../external/+_")
    }
    else {
      return Files.readString(testPaths.baseDir.resolve("bazel-out/community+/darwin_arm64-fastbuild/bin").resolve(paramsPath)).trim()
    }
  }
}

data class TestWorkerPaths(
  @JvmField val baseDir: Path,
  @JvmField val communityDir: Path?,
  val userHomeDir: Path,
)

fun getTestWorkerPaths(): TestWorkerPaths {
  val userHomeDir = Path.of(System.getProperty("user.home"))
  val projectDir = userHomeDir.resolve("projects/idea")
  val communityDir = projectDir.resolve("community")
  val baseDir = getBazelExecRoot(projectDir)
  return TestWorkerPaths(
    baseDir = baseDir,
    communityDir = communityDir,
    userHomeDir = Path.of(System.getProperty("user.home")),
  )
}

fun getTestDataWorkerPaths(): TestWorkerPaths {
  val userHomeDir = Path.of(System.getProperty("user.home"))
  val projectDir = userHomeDir.resolve("projects/idea/community/build/jvm-rules")
  return TestWorkerPaths(
    baseDir = getBazelExecRoot(projectDir),
    communityDir = null,
    userHomeDir = Path.of(System.getProperty("user.home")),
  )
}

private fun getBazelExecRoot(currentWorkingDir: Path): Path {
  val process = ProcessBuilder("bazelisk", "info", "execution_root")
    .directory(currentWorkingDir.toFile())
    .start()

  val output = process.inputStream.readAllBytes().decodeToString().trim()
  val exitCode = process.waitFor()
  if (exitCode == 0) {
    val result = Path.of(output)
    require(Files.isDirectory(result)) {
      "Not a directory: $result (currentWorkingDir=$currentWorkingDir)"
    }
    return result
  }

  throw RuntimeException("Failed to retrieve exec root (output=$output, exitCode=$exitCode)")
}

@OptIn(ExperimentalPathApi::class)
fun collectIjMonorepoSources(sourceDirPath: String, paths: TestWorkerPaths): Sequence<Path> {
  return paths.communityDir!!.resolve(sourceDirPath)
    .walk()
    .filter {
      val p = it.toString()
      p.endsWith(".kt") || p.endsWith(".java")
    }
    .map { "../community+/" + paths.communityDir.relativize(it).invariantSeparatorsPathString }
    .sorted()
    .map { paths.baseDir.resolve(it).normalize() }
}

@OptIn(ExperimentalPathApi::class)
fun collectTestDataSources(sourceDirPath: String, paths: TestWorkerPaths): Sequence<Path> {
  return paths.baseDir.resolve(sourceDirPath)
    .walk()
    .filter {
      val p = it.toString()
      p.endsWith(".kt") || p.endsWith(".java")
    }
    .sorted()
    .map { paths.baseDir.resolve(it).normalize() }
}

fun performTestInvocation(execute: suspend (out: Writer, coroutineScope: CoroutineScope) -> Int) {
  val out = StringWriter()
  val exitCode: Int
  try {
    exitCode = runBlocking(Dispatchers.Default + OpenTelemetryContextElement(Context.root())) {
      execute(out, this)
    }
  }
  finally {
    System.out.appendLine("╔══════════════════════════════════ OUT ══════════════════════════════════╗\n")
    System.out.append(out.toString().trim().prependIndent("║  ") + "\n")
    System.out.appendLine("╚═════════════════════════════════════════════════════════════════════════╝")
  }

  exitProcess(exitCode)
}