// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.codeOptimizer

import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.NullConfiguration
import proguard.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.time.measureTime

internal data class OptimizeLibraryContext(@JvmField val tempDir: Path, @JvmField val javaHome: Path)

// The Maven Java API can appear somewhat complex and broad, making certain tasks cumbersome.
// Hence, we depend on the prerequisite that the original library is already resolved and stored in the local Maven repository.
internal object LibraryCodeOptimizer {
  @JvmStatic
  fun main(args: Array<String>) {
    val m2 = Path.of(System.getProperty("user.home")).resolve(".m2/repository")
    val outDir = Path.of("out").toAbsolutePath()
    val output = outDir.resolve("fastutil.jar")
    if (Files.isRegularFile(output)) {
      Files.deleteIfExists(output)
    }

    //val input = m2.resolve("com/github/weisj/jsvg/1.3.0-jb.3/jsvg-1.3.0-jb.3.jar")
    val version = "8.5.13-jb.1"
    val input = m2.resolve("it/unimi/dsi/fastutil/$version/fastutil-$version.jar")
    val mapping = outDir.resolve("fastutil-map.txt")
    val duration = measureTime {
      optimizeLibrary(name = "fastutil",
                      input = input,
                      output = output,
                      javaHome = System.getProperty("java.home"),
                      mapping = mapping)
    }
    println(duration.inWholeMilliseconds)

    Files.copy(m2.resolve("it/unimi/dsi/fastutil/$version/fastutil-$version-sources.jar"),
               outDir.resolve("fastutil-sources.jar"),
               StandardCopyOption.REPLACE_EXISTING)

    //val digest = sha3_224()
    //updateContentHash(digest, cleanZip(output))
    //println(bytesToHex(digest.digest()))
  }
}

//private fun cleanZip(file: Path): Path {
//  val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".jar")
//  ZipFileWriter(channel = FileChannel.open(tempFile, EnumSet.of(StandardOpenOption.WRITE))).use { zipFileWriter ->
//    readZipFile(file) { name, data ->
//      zipFileWriter.uncompressedData(name, data())
//    }
//  }
//  return tempFile
//}

// See LibraryCodeOptimizer above — it confirms that ProGuard produces the same result for the same input.
// Thus, it is safe to apply ProGuard - builds are reproducible.
internal fun optimizeLibrary(name: String, input: Path, output: Path, javaHome: String, mapping: Path?) {
  val configuration = Configuration()
  val properties = Properties()
  properties.setProperty("java.home", javaHome)
  val configFileName = "$name.conf"
  val configText = OptimizeLibraryContext::class.java.classLoader.getResourceAsStream(configFileName)?.use {
    it.readAllBytes().decodeToString()
  }
  ConfigurationParser(configText, configFileName, output.parent.toFile(), properties).use {
    it.parse(configuration)
  }

  configuration.allowAccessModification = true
  configuration.optimizationPasses = 5
  configuration.optimizeConservatively = false
  configuration.keepParameterNames = true

  configuration.printMapping = mapping?.toFile()

  ConfigurationFactory.setConfigurationFactory(object : ConfigurationFactory() {
    override fun getSupportedTypes(): Array<String>? = null

    override fun getConfiguration(loggerContext: LoggerContext?,
                                  source: ConfigurationSource?): org.apache.logging.log4j.core.config.Configuration {
      return NullConfiguration()
    }
  })

  configuration.programJars = ClassPath()
  val classPathEntry = ClassPathEntry(input.toFile(), false)
  configuration.programJars.add(classPathEntry)
  configuration.programJars.add(ClassPathEntry(output.toFile(), true))
  val proGuard = ProGuard(configuration)
  proGuard.execute()
}