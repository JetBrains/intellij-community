// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.bazel

import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.bazel.JpsModuleToBazel.Companion.searchCommunityRoot
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.system.exitProcess

internal class CompareJpsWithBazel {

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val communityRoot = searchCommunityRoot(Path.of(System.getProperty("user.dir")))
      val ultimateRoot: Path? = if (communityRoot.parent.resolve(".ultimate.root.marker").exists()) {
        communityRoot.parent
      }
      else {
        null
      }

      println("Community root: $communityRoot")
      println("Ultimate root: $ultimateRoot")

      val projectHome = ultimateRoot ?: communityRoot
      val modulesToOutputRoots = BazelTargetsInfo.loadModulesOutputRootsFromBazelTargetsJson(projectHome)

      var allOk = true

      modulesToOutputRoots.forEach { (module, roots) ->
        println("verifying module '$module' ...")

        val productionDir = projectHome.resolve("out/classes/production").resolve(module)
        val testDir = projectHome.resolve("out/classes/test").resolve(module)

        val prodOk = verifySet("production", roots.productionJars, productionDir)
        val testOk = verifySet("test", roots.testJars, testDir)

        if (!prodOk || !testOk) allOk = false
      }

      if (!allOk) {
        println("FAIL")
        exitProcess(1)
      }

      println("OK")
    }

    private fun verifySet(kind: String, jars: List<Path>, classesDir: Path): Boolean {
      val jarFiles = readFromJars(jars)
      val dirFiles = readFromDirectory(classesDir)

      var ok = true
      fun report(msg: String) {
        println("'$kind' mismatch: $msg")
        ok = false
      }

      if (jarFiles.size != dirFiles.size) {
        report("file count differs (jars=${jarFiles.size}, dir=${dirFiles.size})")
      }

      val extraInJar = jarFiles.keys - dirFiles.keys
      if (extraInJar.isNotEmpty()) report("extra in jars: $extraInJar")
      val extraInDir = dirFiles.keys - jarFiles.keys
      if (extraInDir.isNotEmpty()) report("extra in dir: $extraInDir")

      for (name in dirFiles.keys.intersect(jarFiles.keys)) {
        if (!jarFiles[name]!!.contentEquals(dirFiles[name]!!)) {
          report("binary difference: $name")
        }
      }

      return ok
    }

    private fun readFromJars(jars: List<Path>): Map<String, ByteArray> {
      val result = mutableMapOf<String, ByteArray>()
      jars.forEach { jar ->
        ZipFile(jar.toFile()).use { zip ->
          zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .filterNot { isKnownFileToExclude(it.name) }
            .forEach { e ->
              result[e.name] = zip.getInputStream(e).readAllBytes()
            }
        }
      }
      return result
    }

    @OptIn(ExperimentalPathApi::class)
    private fun readFromDirectory(root: Path): Map<String, ByteArray> =
      root.walk()
        .filterNot { it.isDirectory() }
        .filterNot { isKnownFileToExclude(root.relativize(it).invariantSeparatorsPathString) }
        .associate { path ->
          root.relativize(path).invariantSeparatorsPathString to path.readBytes()
        }

    private fun isKnownFileToExclude(path: String): Boolean = when {
      path.startsWith("META-INF") && path.endsWith(".kotlin_module") -> true
      path.startsWith("META-INF/com.jetbrains.rhizomedb.impl.EntityTypeProvider.") -> true  // https://youtrack.jetbrains.com/issue/FL-34023
      else -> false
    }
  }

  private data class ModuleOutputRoots(val productionJars: List<Path>, val testJars: List<Path>)

  private class BazelTargetsInfo {
    companion object {
      fun loadModulesOutputRootsFromBazelTargetsJson(projectRoot: Path): Map<String, ModuleOutputRoots> {
        val bazelTargetsJsonFile = projectRoot.resolve("build").resolve("bazel-targets.json")
        val targetsFile = bazelTargetsJsonFile.inputStream().use { Json.decodeFromStream<TargetsFile>(it) }

        val CONF = "$bazelOsArch-fastbuild"
        return targetsFile.modules.mapValues { (_, targetsFileModuleDescription) ->
          ModuleOutputRoots(
            productionJars = targetsFileModuleDescription.productionJars.map {
              projectRoot.resolve(it.replace("\${CONF}", CONF))
            },
            testJars = targetsFileModuleDescription.testJars.map {
              projectRoot.resolve(it.replace("\${CONF}", CONF))
            },
          )
        }
      }

      private val bazelOsArch = when (OS.CURRENT to CpuArch.CURRENT) {
        OS.Linux to CpuArch.X86_64 -> "k8"
        OS.Linux to CpuArch.ARM64 -> "aarch64"
        OS.Windows to CpuArch.X86_64 -> "x64_windows"
        OS.Windows to CpuArch.ARM64 -> "arm64_windows"
        OS.macOS to CpuArch.ARM64 -> "darwin_arm64"
        OS.macOS to CpuArch.X86_64 -> "darwin_x86_64"
        else -> error("Unsupported OS/Arch: ${OS.CURRENT} ${CpuArch.CURRENT}")
      }
    }

    @Serializable
    data class TargetsFileModuleDescription(
      val productionTargets: List<String>,
      val productionJars: List<String>,
      val testTargets: List<String>,
      val testJars: List<String>,
      val exports: List<String>,
    )

    @Serializable
    data class TargetsFile(
      val modules: Map<String, TargetsFileModuleDescription>,
    )
  }
}
