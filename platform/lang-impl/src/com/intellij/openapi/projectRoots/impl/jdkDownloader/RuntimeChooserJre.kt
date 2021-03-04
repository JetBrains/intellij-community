// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkPopupBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isExecutable

private val LOG = logger<RuntimeChooserJreValidator>()

interface RuntimeChooserJreValidatorCallback<R> {
  fun onSdkResolved(versionString: String, sdkHome: Path): R
  fun onError(@NlsContexts.DialogMessage message: String): R
}

object RuntimeChooserJreValidator {
  @ReviseWhenPortedToJDK("12")
  private val minJdkFeatureVersion
    get() = 11

  fun isSupportedSdkItem(item: JdkItem): Boolean {
    //we do only support mac bundle layout
    if (SystemInfo.isMac && !item.packageToBinJavaPrefix.endsWith("Contents/Home")) {
      return false
    }

    return item.jdkMajorVersion >= minJdkFeatureVersion && item.os == JdkPredicate.currentOS && item.arch == JdkPredicate.currentArch
  }

  fun isSupportedSdkItem(sdk: Sdk) = isSupportedSdkItem({ sdk.versionString }, { sdk.homePath })
  fun isSupportedSdkItem(sdk: SdkPopupBuilder.SuggestedSdk) = isSupportedSdkItem({ sdk.versionString }, { sdk.homePath })

  private inline fun isSupportedSdkItem(versionString: () -> String?, homePath: () -> String?): Boolean = runCatching {
    val version = versionString() ?: return false
    val home = homePath() ?: return false

    //we do only support mac bundle layout
    if (SystemInfo.isMac && !home.endsWith("/Contents/Home")) {
      return false
    }

    val javaVersion = JavaVersion.tryParse(version) ?: return false
    javaVersion.feature >= minJdkFeatureVersion && !WslDistributionManager.isWslPath(home)
  }.getOrDefault(false)

  fun <R> testNewJdkUnderProgress(
    computeHomePath: () -> String?,
    callback: RuntimeChooserJreValidatorCallback<R>,
  ): R {
    val homeDir = runCatching { Path.of(computeHomePath()).toAbsolutePath() }.getOrNull()
                  ?: return callback.onError(
                    LangBundle.message("dialog.message.choose.ide.runtime.set.unknown.error",
                                       LangBundle.message(LangBundle.message("dialog.message.choose.ide.runtime.no.file.part"))))

    if (SystemInfo.isMac && homeDir.endsWith("Contents/Home")) {
      return testNewJdkUnderProgress({ homeDir.parent?.parent?.toString() }, callback)
    }

    if (SystemInfo.isMac && !(homeDir / "Contents" / "Home").isDirectory()) {
      LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. macOS Bundle layout is expected")
      return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.error.mac.bundle", homeDir))
    }

    if (SystemInfo.isWindows && WslDistributionManager.isWslPath(homeDir.toString())) {
      LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. macOS Bundle layout is expected")
      callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.version.error.wsl", homeDir))
    }

    val binJava = when {
      SystemInfo.isWindows -> homeDir / "bin" / "java.exe"
      SystemInfo.isMac -> homeDir / "Contents" / "Home" / "bin" / "java"
      else -> homeDir / "bin" / "java"
    }

    if (!binJava.isFile() || (SystemInfo.isUnix && !binJava.isExecutable())) {
      LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. Failed to find bin/java executable at $binJava")
      return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.cannot.start.error", homeDir))
    }

    val info = runCatching {
      //we compute the path to handle macOS bundle layout once again here
      val inferredHome = binJava.parent?.parent?.toString() ?: return@runCatching null
      JdkVersionDetector.getInstance().detectJdkVersionInfo(inferredHome)
    }.getOrNull() ?: return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.unknown.error", homeDir))

    if (info.version.feature < minJdkFeatureVersion) {
      LOG.warn("Failed to scan JDK for boot runtime: ${homeDir}. The version $info is less than $minJdkFeatureVersion")
      return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.version.error", homeDir, "11",
                                                 info.version.toString()))
    }

    val jdkVersion = info.version?.toString()
                     ?: return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.unknown.error", homeDir))

    try {
      val cmd = GeneralCommandLine(binJava.toString(), "-version")
      val exitCode = CapturingProcessHandler(cmd).runProcess(30_000).exitCode
      if (exitCode != 0) {
        LOG.warn("Failed to run JDK for boot runtime: ${homeDir}. Exit code is ${exitCode} for $binJava.")
        return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.cannot.start.error", homeDir))
      }
    }
    catch (t: Throwable) {
      if (t is ControlFlowException) throw t
      LOG.warn("Failed to run JDK for boot runtime: $homeDir. ${t.message}", t)
      return callback.onError(LangBundle.message("dialog.message.choose.ide.runtime.set.cannot.start.error", homeDir))
    }

    val versionString = listOfNotNull(info.displayName, jdkVersion).joinToString(" ")
    return callback.onSdkResolved(versionString, homeDir)
  }
}
