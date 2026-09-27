// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.moduleRepository.MODULE_DESCRIPTORS_COMPACT_PATH

/**
 * The JVM arguments of [jvm] for [os] and [arch], as `BuildContext.getAdditionalJvmArguments` states them.
 *
 * [openedPackages] are the `--add-opens` lines of [os]. [isScript] quotes the arguments that hold a path macro.
 * [isPortableDist] drops `/Contents` from the macOS macro. [isQodana] leaves the multi-routing file system out.
 */
@ApiStatus.Internal
fun renderAdditionalJvmArguments(
  jvm: ProductJvmArguments,
  os: OsFamily,
  arch: JvmArchitecture,
  openedPackages: List<String>,
  isScript: Boolean = false,
  isPortableDist: Boolean = false,
  isQodana: Boolean = false,
): List<String> {
  fun String.quoteIfNeeded(): String = if (isScript) "\"$this\"" else this

  val result = ArrayList<String>()
  val macroName = when (os) {
    OsFamily.WINDOWS -> "%IDE_HOME%"
    OsFamily.MACOS -> $$"$APP_PACKAGE$${if (isPortableDist) "" else "/Contents"}"
    OsFamily.LINUX -> $$"$IDE_HOME"
  }

  val bootClassPathJarNames = jvm.xBootClassPathJarNames + if (!isQodana && jvm.multiRoutingFileSystem) listOf(PLATFORM_CORE_NIO_FS) else emptyList()
  if (bootClassPathJarNames.isNotEmpty()) {
    val (pathSeparator, dirSeparator) = if (os == OsFamily.WINDOWS) ";" to "\\" else ":" to "/"
    val bootClassPath = bootClassPathJarNames.joinToString(pathSeparator) { arrayOf(macroName, "lib", it).joinToString(dirSeparator) }
    result.add("-Xbootclasspath/a:$bootClassPath".quoteIfNeeded())
  }

  if (jvm.cdsArchiveFileName != null) {
    val cacheDir = if (os == OsFamily.WINDOWS) "%IDE_CACHE_DIR%\\" else $$"$IDE_CACHE_DIR/"
    result.add("-XX:SharedArchiveFile=$cacheDir${jvm.cdsArchiveFileName}")
    result.add("-XX:+AutoCreateSharedArchive")
  }
  else {
    jvm.classLoader?.let {
      result.add("-Djava.system.class.loader=$it")
    }
  }

  result.add("-Didea.vendor.name=${jvm.vendorName}")
  result.add("-Didea.paths.selector=${jvm.pathsSelector}")

  if (jvm.jna) {
    result.add("-Djna.boot.library.path=$macroName/lib/jna/${arch.dirName}".quoteIfNeeded())
    result.add("-Djna.nosys=true")
    result.add("-Djna.noclasspath=true")
  }
  if (jvm.pty4j) {
    result.add("-Dpty4j.preferred.native.folder=$macroName/lib/pty4j".quoteIfNeeded())
  }
  result.add("-Dio.netty.allocator.type=pooled")
  if (jvm.skiko) {
    result.add("-Dskiko.library.path=$macroName/lib/skiko-awt-runtime-all".quoteIfNeeded())
  }

  if (jvm.runtimeModuleRepository) {
    result.add("-Dintellij.platform.runtime.repository.path=$macroName/$MODULE_DESCRIPTORS_COMPACT_PATH".quoteIfNeeded())
  }
  if (jvm.rootModule != null) {
    result.add("-Dintellij.platform.root.module=${jvm.rootModule}")
    result.add("-Dintellij.platform.product.mode=${jvm.productMode}")
  }

  jvm.platformPrefix?.let {
    result.add("-Didea.platform.prefix=$it")
  }

  result.addAll(jvm.additional)

  if (jvm.splash) {
    @Suppress("SpellCheckingInspection", "RedundantSuppression")
    result.add("-Dsplash=true")
  }

  // https://youtrack.jetbrains.com/issue/IDEA-269280
  result.add("-Daether.connector.resumeDownloads=false")
  result.add("-Dcompose.swing.render.on.graphics=true")

  if (jvm.nativeAccess) {
    result.add("--enable-native-access=ALL-UNNAMED")
  }

  result.addAll(openedPackages)
  return result
}
