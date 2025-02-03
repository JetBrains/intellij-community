// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

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

enum class TestModules(@JvmField val sourcePath: String, private val paramsPath: String) {
  XML_DOM("platform/util/xmlDom/src", "platform/util/xmlDom/xmlDom.jar-0.params"),
  PLATFORM_IMPL("platform/platform-impl/src", "platform/platform-impl/ide-impl.jar-0.params"),
  LANG_IMPL("platform/lang-impl/src", "platform/lang-impl/lang-impl.jar-0.params"),
  PLATFORM_BOOTSTRAP("platform/platform-impl/bootstrap/src", "platform/platform-impl/bootstrap/ide-bootstrap-kt.jar-0.params");

  fun getParams(baseDir: Path): String {
    @Suppress("SpellCheckingInspection")
    return Files.readString(baseDir.resolve("bazel-out/community+/darwin_arm64-fastbuild/bin").resolve(paramsPath)).trim()
  }
}

data class TestWorkerPaths(
  @JvmField val baseDir: Path,
  @JvmField val communityDir: Path,
  val userHomeDir: Path,
)

fun getTestWorkerPaths(): TestWorkerPaths {
  val userHomeDir = Path.of(System.getProperty("user.home"))
  val ideaProjectDirName = "idea"
  val projectDir = userHomeDir.resolve("projects/$ideaProjectDirName")
  val communityDir = projectDir.resolve("community")
  val baseDir = getBazelExecRoot(projectDir)
  return TestWorkerPaths(
    baseDir = baseDir,
    communityDir = communityDir,
    userHomeDir = Path.of(System.getProperty("user.home")),
  )
}

private fun getBazelExecRoot(currentWorkingDir: Path): Path {
  val process = ProcessBuilder("bazelisk", "info", "execution_root")
    .directory(currentWorkingDir.toFile())
    .start()

  val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
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
fun collectSources(sourceDirPath: String, paths: TestWorkerPaths): List<Path> {
  val result = paths.communityDir.resolve(sourceDirPath)
    .walk()
    .filter {
      val p = it.toString()
      p.endsWith(".kt") || p.endsWith(".java")
    }
    .map { "../community+/" + paths.communityDir.relativize(it).invariantSeparatorsPathString }
    .sorted()
    .map { paths.baseDir.resolve(it).normalize() }
    .toList()
  require(result.isNotEmpty())
  return result
}

fun performTestInvocation(execute: suspend (out: Writer, coroutineScope: CoroutineScope) -> Int) {
  val out = StringWriter()
  val exitCode: Int
  try {
    exitCode = runBlocking(Dispatchers.Default) {
      execute(out, this)
    }
  }
  finally {
    System.out.append(out.toString())
  }

  exitProcess(exitCode)
}