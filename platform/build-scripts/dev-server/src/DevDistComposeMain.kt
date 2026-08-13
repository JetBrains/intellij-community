// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistComposeMain")
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.devIdeConfig.DevIdeConfig
import org.jetbrains.intellij.build.dev.computeIdeFingerprintFromComponents
import org.jetbrains.intellij.build.dev.readDevBuildComponentManifest
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
  val options = parseComposeArgs(args)
  val platformDir = options.requiredPath("--platform-dir")
  val pluginsDir = options.requiredPath("--plugins-dir")
  val outputDir = options.requiredPath("--output-dir")
  val platformManifest = readDevBuildComponentManifest(options.requiredPath("--platform-manifest"))
  val pluginsManifest = readDevBuildComponentManifest(options.requiredPath("--plugins-manifest"))
  val ideConfig = options.requiredPath("--ide-config")

  check(platformManifest.kind == "platform") { "Expected a platform component, got '${platformManifest.kind}'" }
  check(pluginsManifest.kind == "plugins") { "Expected a plugins component, got '${pluginsManifest.kind}'" }
  check(platformManifest.platformPrefix == pluginsManifest.platformPrefix) { "Dev-build components have different products" }
  check(platformManifest.os == pluginsManifest.os && platformManifest.arch == pluginsManifest.arch) {
    "Dev-build components have different target platforms"
  }
  check(platformManifest.mainClass == pluginsManifest.mainClass) { "Dev-build components have different IDE main classes" }

  if (Files.exists(outputDir)) outputDir.deleteRecursively()
  Files.createDirectories(outputDir)
  mergeComponent(platformDir, outputDir)
  mergeComponent(pluginsDir, outputDir)

  val classPath = platformManifest.coreClassPath + pluginsManifest.coreClassPath
  Files.writeString(outputDir.resolve("core-classpath.txt"), classPath.joinToString(separator = "\n"))
  Files.writeString(outputDir.resolve("fingerprint.txt"), computeIdeFingerprintFromComponents(listOf(platformManifest, pluginsManifest)))
  DevIdeConfig.write(
    ideConfig,
    outputDir,
    platformManifest.mainClass,
    platformManifest.platformPrefix,
    pluginsManifest.additionalModules,
  )
}

private fun mergeComponent(source: Path, target: Path) {
  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(target.resolve(source.relativize(dir).toString()))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val destination = target.resolve(source.relativize(file).toString())
      check(!Files.exists(destination)) { "Dev-build components both provide '${target.relativize(destination)}'" }
      if (Files.isSymbolicLink(file)) {
        Files.createSymbolicLink(destination, Files.readSymbolicLink(file))
      }
      else {
        try {
          Files.createLink(destination, file)
        }
        catch (_: IOException) {
          Files.copy(file, destination)
        }
      }
      return FileVisitResult.CONTINUE
    }
  })
}

private class ComposeOptions(private val values: Map<String, String>) {
  fun requiredPath(name: String): Path {
    return Path.of(values.get(name) ?: error("$name is required")).toAbsolutePath().normalize()
  }
}

private fun parseComposeArgs(args: Array<String>): ComposeOptions {
  val values = LinkedHashMap<String, String>()
  for (arg in args) {
    val separator = arg.indexOf('=')
    require(arg.startsWith("--") && separator > 2) { "Expected an option in the '--key=value' form, but got '$arg'" }
    val name = arg.substring(0, separator)
    check(values.put(name, arg.substring(separator + 1)) == null) { "$name must be specified at most once" }
  }
  return ComposeOptions(values)
}
