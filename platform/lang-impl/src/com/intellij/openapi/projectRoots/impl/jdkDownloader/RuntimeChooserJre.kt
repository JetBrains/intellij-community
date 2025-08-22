// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.wsl.WslPath
import com.intellij.lang.LangBundle
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkPopupBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

private val LOG = logger<RuntimeChooserJreValidator>()

internal interface RuntimeChooserJreValidatorCallback<R> {
  fun onSdkResolved(displayName: String?, versionString: String, sdkHome: Path): R
  fun onError(@NlsContexts.DialogMessage message: String): R
}

internal object RuntimeChooserJreValidator {
  @ReviseWhenPortedToJDK("22")
  private val minJdkFeatureVersion
    get() = 21

  fun isSupportedSdkItem(item: JdkItem): Boolean {
    // TODO Introduce EelApi here.
    //we do only support mac bundle layout
    if (SystemInfo.isMac && !item.packageToBinJavaPrefix.endsWith("Contents/Home")) {
      return false
    }

    return item.jdkMajorVersion >= minJdkFeatureVersion && item.os == JdkPredicate.currentOS && item.arch == JdkPredicate.currentArch
  }

  fun isSupportedSdkItem(sdk: Sdk): Boolean = isSupportedSdkItem({ sdk.versionString }, { sdk.homePath })
  fun isSupportedSdkItem(sdk: SdkPopupBuilder.SuggestedSdk): Boolean = isSupportedSdkItem({ sdk.versionString }, { sdk.homePath })

  private inline fun isSupportedSdkItem(versionString: () -> String?, homePath: () -> String?): Boolean = runCatching {
    val version = versionString() ?: return false
    val home = homePath() ?: return false

    //we do only support mac bundle layout
    if (SystemInfo.isMac && !home.endsWith("/Contents/Home")) {
      return false
    }

    val javaVersion = JavaVersion.tryParse(version) ?: return false
    javaVersion.feature >= minJdkFeatureVersion && !WslPath.isWslUncPath(home)
  }.getOrDefault(false)

  fun <R> testNewJdkUnderProgress(
    allowRunProcesses: Boolean,
    computeHomePath: () -> String?,
    callback: RuntimeChooserJreValidatorCallback<R>,
    hideLogs: Boolean = false,
  ): R {
    val homeDir = runCatching { Path.of(computeHomePath()).toAbsolutePath() }.getOrNull()
                  ?: return callback.onError(
                    LangBundle.message("dialog.message.choose.ide.runtime.set.unknown.error", LangBundle.message("dialog.message.choose.ide.runtime.no.file.part")))

    if (SystemInfo.isMac && homeDir.toString().endsWith("/Contents/Home")) {
      return testNewJdkUnderProgress(allowRunProcesses, { homeDir.parent?.parent?.toString() }, callback)
    }

    if (SystemInfo.isMac && homeDir.fileName.toString() == "Contents" && (homeDir / "Home").isDirectory()) {
      return testNewJdkUnderProgress(allowRunProcesses, { homeDir.parent?.toString() }, callback)
    }

    if (SystemInfo.isMac && !(homeDir / "Contents" / "Home").isDirectory()) {
      if (!hideLogs) LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. macOS Bundle layout is expected")
      return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.error.mac.bundle", homeDir))
    }

    if (SystemInfo.isWindows && WslPath.isWslUncPath(homeDir.toString())) {
      if (!hideLogs) LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. macOS Bundle layout is expected")
      callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.version.error.wsl", homeDir))
    }

    val binJava = when {
      SystemInfo.isWindows -> homeDir / "bin" / "java.exe"
      SystemInfo.isMac -> homeDir / "Contents" / "Home" / "bin" / "java"
      else -> homeDir / "bin" / "java"
    }

    if (!binJava.isRegularFile() || (SystemInfo.isUnix && !binJava.isExecutable())) {
      if (!hideLogs) LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. Failed to find bin/java executable at $binJava")
      return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.cannot.start.error", homeDir))
    }

    val info = runCatching {
      //we compute the path to handle macOS bundle layout once again here
      val inferredHome = binJava.parent?.parent?.toString() ?: return@runCatching null
      JdkVersionDetector.getInstance().detectJdkVersionInfo(inferredHome)
    }.getOrNull() ?: return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.unknown.error", homeDir))

    if (info.version == null || info.version.feature < minJdkFeatureVersion) {
      if (!hideLogs) LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. The version $info is less than $minJdkFeatureVersion")
      return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.version.error", homeDir, minJdkFeatureVersion,
                                                 info.version.toString()))
    }

    val jdkVersion = tryComputeAdvancedFullVersion(binJava)
                     ?: info.version?.toString()
                     ?: return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.unknown.error", homeDir))

    if (allowRunProcesses) {
      try {
        val cmd = GeneralCommandLine(binJava.toString(), "-version")
        val exitCode = CapturingProcessHandler(cmd).runProcess(30_000).exitCode
        if (exitCode != 0) {
          if (!hideLogs) LOG.warn("Failed to run JDK for boot runtime: ${homeDir}. Exit code is ${exitCode} for $binJava.")
          return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.cannot.start.error", homeDir))
        }
      }
      catch (t: Throwable) {
        if (t is ControlFlowException) throw t
        if (!hideLogs) LOG.warn("Failed to run JDK for boot runtime: $homeDir. ${t.message}", t)
        return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.cannot.start.error", homeDir))
      }
    }

    return callback.onSdkResolved(info.variant.displayName, jdkVersion, homeDir)
  }
}

private class ReleaseProperties(releaseFile: Path) {
  private val p = Properties()

  init {
    runCatching {
      if (Files.isRegularFile(releaseFile)) {
        Files.newInputStream(releaseFile).use { p.load(it) }
      }
    }
  }

  fun getJdkProperty(name: String) = p
    .getProperty(name)
    ?.trim()
    ?.removeSurrounding("\"")
    ?.trim()
}

private fun tryComputeAdvancedFullVersion(binJava: Path): String? = runCatching {
  //we compute the path to handle macOS bundle layout once again here
  val theReleaseFile = binJava.parent?.parent?.resolve("release") ?: return@runCatching null
  val p = ReleaseProperties(theReleaseFile)

  val implementor = p.getJdkProperty("IMPLEMENTOR")
  when {
    implementor.isNullOrBlank() -> null

    implementor.startsWith("JetBrains") -> {
      p.getJdkProperty("IMPLEMENTOR_VERSION")
        ?.removePrefix("JBR-")
        ?.replace("JBRSDK-", "JBRSDK ")
        ?.trim()
    }

    implementor.startsWith("Azul") -> {
      listOfNotNull(
        p.getJdkProperty("JAVA_VERSION"),
        p.getJdkProperty("IMPLEMENTOR_VERSION")
      ).joinToString(" ").takeIf { it.isNotBlank() }
    }

    implementor.startsWith("Amazon.com") -> {
      val implVersion = p.getJdkProperty("IMPLEMENTOR_VERSION")
      if (implVersion != null && implVersion.startsWith("Corretto-")) {
        implVersion.removePrefix("Corretto-")
      } else null
    }
    else -> null
  }
}.getOrNull()
