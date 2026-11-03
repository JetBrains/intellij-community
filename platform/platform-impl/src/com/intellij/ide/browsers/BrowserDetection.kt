// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

@ApiStatus.Internal
@OptIn(LowLevelLocalMachineAccess::class) // we don't need eel here because we need to get the client OS, not the project's one
fun detectBrowserBinary(
  browser: WebBrowser,
  userHome: Path? = null,
  env: Map<String, String> = emptyMap(),
  fs: FileSystem = FileSystems.getDefault(),
  os: OS = OS.CURRENT,
): Result<Path> {
  val browserPath = browser.path?.let(fs::getPath)
  if (browserPath != null && browserPath.isExecutable()) {
    return Result.success(browserPath)
  }

  // An explicit absolute path was configured but is missing or not executable: fail instead of silently falling
  // back to a different installed browser resolved by name-based detection (which could launch the wrong browser).
  if (browserPath != null && browserPath.isAbsolute && !browserPath.exists()) {
    return Result.failure(BrowserExecutableNotFoundException(browser))
  }

  val detectedBrowser = detectBrowserType(browser) ?: return Result.failure(
    BrowserExecutableNotFoundException(browser)
  )

  val pathToBinary = findBrowserBinary(detectedBrowser, os, env, fs, userHome)
  if (pathToBinary != null) {
    return Result.success(pathToBinary)
  }
  return Result.failure(BrowserExecutableNotFoundException(browser))
}

/**
 * Sealed class hierarchy representing well-known browsers
 */
@ApiStatus.Internal
sealed class DetectedBrowser {
  sealed class BrowserWithQuality(open val quality: BrowserQuality) : DetectedBrowser()

  data class Chrome(override val quality: ChromeQuality) : BrowserWithQuality(quality)
  data class Edge(override val quality: EdgeQuality) : BrowserWithQuality(quality)
  data object Chromium : DetectedBrowser()
  data object Opera : DetectedBrowser()
  data object Brave : DetectedBrowser()
  data object Vivaldi : DetectedBrowser()
}

@ApiStatus.Internal
sealed class BrowserQuality(
  val windowsFolder: String,
  val macAppName: String,
  val linuxExecutables: List<String>,
)

@ApiStatus.Internal
sealed class ChromeQuality(windowsFolder: String, macAppName: String, linuxExecutables: List<String>) : BrowserQuality(windowsFolder, macAppName, linuxExecutables) {
  data object Stable : ChromeQuality(
    windowsFolder = "Google\\Chrome\\Application\\chrome.exe",
    macAppName = "Google Chrome",
    linuxExecutables = listOf("google-chrome", "google-chrome-stable", "chrome")
  )

  data object Beta : ChromeQuality(
    windowsFolder = "Google\\Chrome Beta\\Application\\chrome.exe",
    macAppName = "Google Chrome Beta",
    linuxExecutables = listOf("google-chrome-beta")
  )

  data object Dev : ChromeQuality(
    windowsFolder = "Google\\Chrome Dev\\Application\\chrome.exe",
    macAppName = "Google Chrome Dev",
    linuxExecutables = listOf("google-chrome-unstable", "google-chrome-dev")
  )

  data object Canary : ChromeQuality(
    windowsFolder = "Google\\Chrome SxS\\Application\\chrome.exe",
    macAppName = "Google Chrome Canary",
    linuxExecutables = listOf("google-chrome-canary")
  )
}

@ApiStatus.Internal
sealed class EdgeQuality(windowsFolder: String, macAppName: String, linuxExecutables: List<String>) : BrowserQuality(windowsFolder, macAppName, linuxExecutables) {
  data object Stable : EdgeQuality(
    windowsFolder = "Microsoft\\Edge\\Application\\msedge.exe",
    macAppName = "Microsoft Edge",
    linuxExecutables = listOf("microsoft-edge", "microsoft-edge-stable")
  )

  data object Beta : EdgeQuality(
    windowsFolder = "Microsoft\\Edge Beta\\Application\\msedge.exe",
    macAppName = "Microsoft Edge Beta",
    linuxExecutables = listOf("microsoft-edge-beta")
  )

  data object Dev : EdgeQuality(
    windowsFolder = "Microsoft\\Edge Dev\\Application\\msedge.exe",
    macAppName = "Microsoft Edge Dev",
    linuxExecutables = listOf("microsoft-edge-dev")
  )

  data object Canary : EdgeQuality(
    windowsFolder = "Microsoft\\Edge SxS\\Application\\msedge.exe",
    macAppName = "Microsoft Edge Canary",
    linuxExecutables = listOf("microsoft-edge-canary")
  )
}

private fun findBrowserBinary(
  browser: DetectedBrowser,
  os: OS,
  env: Map<String, String>,
  fs: FileSystem,
  userHome: Path?,
): Path? {
  val browser = browser as? DetectedBrowser.BrowserWithQuality
                ?: return null

  val candidates = when (os) {
    OS.Windows -> windowsCandidates(browser, env, fs)
    OS.macOS -> macosCandidates(browser, env, fs, userHome)
    OS.FreeBSD, OS.Linux -> linuxCandidates(browser, env, fs)
    OS.OHOS -> harmonyOSCandidates(browser, env, fs)
    OS.Other -> emptySequence()
  }

  return candidates.filter { it.isExecutable() }.firstOrNull()
}

private fun windowsCandidates(browser: DetectedBrowser.BrowserWithQuality, env: Map<String, String>, fs: FileSystem): Sequence<Path> = sequence {
  for (envVar in arrayOf("LOCALAPPDATA", "ProgramFiles", "ProgramFiles(x86)")) {
    val path = env[envVar]?.let(fs::getPath) ?: continue
    yield(path.resolve(browser.quality.windowsFolder))
  }
}

private fun linuxCandidates(browser: DetectedBrowser.BrowserWithQuality, env: Map<String, String>, fs: FileSystem): Sequence<Path> = sequence {
  for (installationDir in arrayOf("/usr/local/bin", "/opt/local/bin", "/usr/bin", "/opt/bin")) {
    for (executableName in browser.quality.linuxExecutables) {
      yield(fs.getPath(installationDir, executableName))
    }
  }
  for (executableName in browser.quality.linuxExecutables) {
    yield(fs.getPath("/usr/local", executableName, "bin", executableName))
  }
}

private fun macosCandidates(browser: DetectedBrowser.BrowserWithQuality, env: Map<String, String>, fs: FileSystem, userHome: Path?): Sequence<Path> {
  val appName = browser.quality.macAppName
  return listOfNotNull(fs.getPath("/Applications"), userHome?.resolve("Applications"))
    .asSequence()
    .map { it.resolve("${appName}.app").resolve("Contents").resolve("MacOS").resolve(appName) }
}

private fun harmonyOSCandidates(browser: DetectedBrowser.BrowserWithQuality, env: Map<String, String>, fs: FileSystem): Sequence<Path> {
  //todo[kb] implement
  return emptySequence()
}

private fun findInNameAndPath(what: String, browser: WebBrowser): Boolean {
  return browser.name.contains(what, ignoreCase = true) ||
         browser.path?.contains(what, ignoreCase = true) == true
}

/**
 * Guessing browser type from [WebBrowser] name and path.
 */
@ApiStatus.Internal
fun detectBrowserType(browser: WebBrowser): DetectedBrowser? {
  return when {
    findInNameAndPath("chromium", browser) -> DetectedBrowser.Chromium
    findInNameAndPath("chrome", browser) -> {
      val quality = when {
        findInNameAndPath("canary", browser) || findInNameAndPath("sxs", browser) -> ChromeQuality.Canary
        findInNameAndPath("dev", browser) || findInNameAndPath("unstable", browser) -> ChromeQuality.Dev
        findInNameAndPath("beta", browser) -> ChromeQuality.Beta
        else -> ChromeQuality.Stable
      }
      DetectedBrowser.Chrome(quality)
    }

    findInNameAndPath("edge", browser) -> {
      val quality = when {
        findInNameAndPath("canary", browser) || findInNameAndPath("sxs", browser) -> EdgeQuality.Canary
        findInNameAndPath("dev", browser) -> EdgeQuality.Dev
        findInNameAndPath("beta", browser) -> EdgeQuality.Beta
        else -> EdgeQuality.Stable
      }
      DetectedBrowser.Edge(quality)
    }

    findInNameAndPath("opera", browser) -> DetectedBrowser.Opera
    findInNameAndPath("brave", browser) -> DetectedBrowser.Brave
    findInNameAndPath("vivaldi", browser) -> DetectedBrowser.Vivaldi

    else -> null
  }
}

@ApiStatus.Internal
class BrowserExecutableNotFoundException(browser: WebBrowser) : Exception() {
  @get:NlsContexts.DialogMessage
  override val message: String = IdeBundle.message("dialog.message.could.not.find.browser.executable", browser.name)
}
