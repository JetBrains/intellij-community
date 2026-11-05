// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
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
  val failure = Result.failure<Path>(BrowserExecutableNotFoundException(browser))
  if (browserPath != null && browserPath.isAbsolute && !browserPath.exists()) {
    return failure
  }

  val detectedBrowser = detectBrowserType(browser) ?: return failure
  val pathToBinary = findBrowserBinary(detectedBrowser, os, env, fs, userHome) ?: return failure

  return Result.success(pathToBinary)
}

/**
 * A browser that [detectInstalledBrowsers] found in a default install location.
 *
 * [path] is the short form to store in the browser list:
 * the app name on macOS, for `open -a`,
 * the executable name on Linux when the directory of the executable is in `PATH`,
 * and the full path of [binary] otherwise.
 * [bundle] is the app bundle on macOS.
 */
@ApiStatus.Internal
data class InstalledBrowser(
  @NlsSafe val name: String,
  @NlsSafe val path: String,
  val binary: Path,
  val bundle: Path?,
  val knownBrowser: KnownBrowser,
) {
  /**
   * Returns `true` when [browser] opens this installed browser. The settings of [browser], such as its arguments, do not matter.
   *
   * The path of [browser] can be [path], [binary] or [bundle].
   * A path without a directory, such as `google-chrome`, can also name this browser by its version, see [detectBrowserType].
   */
  fun isSameAs(browser: WebBrowser): Boolean {
    val browserPath = browser.path?.let(FileUtil::toSystemIndependentName) ?: return false
    val knownPaths = listOfNotNull(path, binary.toString(), bundle?.toString()).map(FileUtil::toSystemIndependentName)
    if (knownPaths.any { FileUtil.pathsEqual(it, browserPath) }) {
      return true
    }
    return '/' !in browserPath && detectBrowserType(browser) == knownBrowser
  }
}

/**
 * Returns the known browsers that are installed in their default locations, see [KnownBrowser].
 * It checks the file system, so call it on a background thread.
 */
@ApiStatus.Internal
@OptIn(LowLevelLocalMachineAccess::class) // we don't need eel here because we need to get the client OS, not the project's one
fun detectInstalledBrowsers(
  userHome: Path? = null,
  env: Map<String, String> = emptyMap(),
  fs: FileSystem = FileSystems.getDefault(),
  os: OS = OS.CURRENT,
): List<InstalledBrowser> {
  val pathDirectories = env["PATH"]?.split(':')?.filter { it.isNotEmpty() }?.map(fs::getPath)?.toSet() ?: emptySet()
  return KnownBrowser.entries.mapNotNull { knownBrowser ->
    val binary = findBrowserBinary(knownBrowser, os, env, fs, userHome) ?: return@mapNotNull null
    val name = knownBrowser.macAppName
    when (os) {
      OS.macOS -> InstalledBrowser(name, name, binary, binary.parent.parent.parent, knownBrowser)
      OS.FreeBSD, OS.Linux -> {
        val path = if (binary.parent in pathDirectories) binary.fileName.toString() else binary.toString()
        InstalledBrowser(name, path, binary, null, knownBrowser)
      }
      else -> InstalledBrowser(name, binary.toString(), binary, null, knownBrowser)
    }
  }
}

/**
 * A version of a well-known browser and its default install locations.
 *
 * [windowsExecutable] is the path of the executable relative to `LOCALAPPDATA`, `ProgramFiles` and `ProgramFiles(x86)`,
 * or `null` without a Windows version.
 * [macAppName] is the name of the macOS app, and [macExecutable] is the executable inside `Contents/MacOS` of the app.
 * [linuxExecutables] are the names of the executable in the usual Linux directories.
 */
@ApiStatus.Internal
enum class KnownBrowser(
  val family: BrowserFamily,
  val windowsExecutable: String?,
  val macAppName: String,
  val linuxExecutables: List<String>,
  val macExecutable: String = macAppName,
) {
  CHROME(BrowserFamily.CHROME, "Google\\Chrome\\Application\\chrome.exe", "Google Chrome",
         listOf("google-chrome", "google-chrome-stable", "chrome")),
  CHROME_BETA(BrowserFamily.CHROME, "Google\\Chrome Beta\\Application\\chrome.exe", "Google Chrome Beta",
              listOf("google-chrome-beta")),
  CHROME_DEV(BrowserFamily.CHROME, "Google\\Chrome Dev\\Application\\chrome.exe", "Google Chrome Dev",
             listOf("google-chrome-unstable", "google-chrome-dev")),
  CHROME_CANARY(BrowserFamily.CHROME, "Google\\Chrome SxS\\Application\\chrome.exe", "Google Chrome Canary",
                listOf("google-chrome-canary")),

  EDGE(BrowserFamily.CHROME, "Microsoft\\Edge\\Application\\msedge.exe", "Microsoft Edge",
       listOf("microsoft-edge", "microsoft-edge-stable")),
  EDGE_BETA(BrowserFamily.CHROME, "Microsoft\\Edge Beta\\Application\\msedge.exe", "Microsoft Edge Beta",
            listOf("microsoft-edge-beta")),
  EDGE_DEV(BrowserFamily.CHROME, "Microsoft\\Edge Dev\\Application\\msedge.exe", "Microsoft Edge Dev",
           listOf("microsoft-edge-dev")),
  EDGE_CANARY(BrowserFamily.CHROME, "Microsoft\\Edge SxS\\Application\\msedge.exe", "Microsoft Edge Canary",
              listOf("microsoft-edge-canary")),

  BRAVE(BrowserFamily.CHROME, "BraveSoftware\\Brave-Browser\\Application\\brave.exe", "Brave Browser",
        listOf("brave-browser", "brave-browser-stable", "brave")),
  BRAVE_BETA(BrowserFamily.CHROME, "BraveSoftware\\Brave-Browser-Beta\\Application\\brave.exe", "Brave Browser Beta",
             listOf("brave-browser-beta")),
  BRAVE_NIGHTLY(BrowserFamily.CHROME, "BraveSoftware\\Brave-Browser-Nightly\\Application\\brave.exe", "Brave Browser Nightly",
                listOf("brave-browser-nightly")),

  VIVALDI(BrowserFamily.CHROME, "Vivaldi\\Application\\vivaldi.exe", "Vivaldi", listOf("vivaldi", "vivaldi-stable")),
  VIVALDI_SNAPSHOT(BrowserFamily.CHROME, "Vivaldi Snapshot\\Application\\vivaldi.exe", "Vivaldi Snapshot", listOf("vivaldi-snapshot")),

  OPERA(BrowserFamily.CHROME, "Programs\\Opera\\opera.exe", "Opera", listOf("opera")),
  OPERA_BETA(BrowserFamily.CHROME, "Programs\\Opera beta\\opera.exe", "Opera Beta", listOf("opera-beta")),
  OPERA_DEVELOPER(BrowserFamily.CHROME, "Programs\\Opera developer\\opera.exe", "Opera Developer", listOf("opera-developer")),
  OPERA_GX(BrowserFamily.CHROME, "Programs\\Opera GX\\opera.exe", "Opera GX", emptyList()),

  CHROMIUM(BrowserFamily.CHROME, "Chromium\\Application\\chrome.exe", "Chromium", listOf("chromium", "chromium-browser")),

  FIREFOX(BrowserFamily.FIREFOX, "Mozilla Firefox\\firefox.exe", "Firefox", listOf("firefox", "firefox-esr"), macExecutable = "firefox"),
  FIREFOX_DEVELOPER_EDITION(BrowserFamily.FIREFOX, "Firefox Developer Edition\\firefox.exe", "Firefox Developer Edition",
                            listOf("firefox-developer-edition"), macExecutable = "firefox"),
  FIREFOX_NIGHTLY(BrowserFamily.FIREFOX, "Firefox Nightly\\firefox.exe", "Firefox Nightly", listOf("firefox-nightly"),
                  macExecutable = "firefox"),

  SAFARI(BrowserFamily.SAFARI, null, "Safari", emptyList()),
  SAFARI_TECHNOLOGY_PREVIEW(BrowserFamily.SAFARI, null, "Safari Technology Preview", emptyList()),
}

private fun findBrowserBinary(
  knownBrowser: KnownBrowser,
  os: OS,
  env: Map<String, String>,
  fs: FileSystem,
  userHome: Path?,
): Path? {
  val candidates = when (os) {
    OS.Windows -> windowsCandidates(knownBrowser, env, fs)
    OS.macOS -> macosCandidates(knownBrowser, fs, userHome)
    OS.FreeBSD, OS.Linux -> linuxCandidates(knownBrowser, fs)
    OS.OHOS -> harmonyOSCandidates()
    OS.Other -> emptySequence()
  }

  return candidates.firstOrNull { it.isExecutable() }
}

private fun windowsCandidates(knownBrowser: KnownBrowser, env: Map<String, String>, fs: FileSystem): Sequence<Path> = sequence {
  val windowsExecutable = knownBrowser.windowsExecutable ?: return@sequence
  for (envVar in arrayOf("LOCALAPPDATA", "ProgramFiles", "ProgramFiles(x86)")) {
    val path = env[envVar]?.let(fs::getPath) ?: continue
    yield(path.resolve(windowsExecutable))
  }
}

private fun linuxCandidates(knownBrowser: KnownBrowser, fs: FileSystem): Sequence<Path> = sequence {
  for (installationDir in arrayOf("/usr/local/bin", "/opt/local/bin", "/usr/bin", "/opt/bin", "/snap/bin")) {
    for (executableName in knownBrowser.linuxExecutables) {
      yield(fs.getPath(installationDir, executableName))
    }
  }
  for (executableName in knownBrowser.linuxExecutables) {
    yield(fs.getPath("/usr/local", executableName, "bin", executableName))
  }
}

private fun macosCandidates(knownBrowser: KnownBrowser, fs: FileSystem, userHome: Path?): Sequence<Path> {
  val appName = knownBrowser.macAppName
  return listOfNotNull(fs.getPath("/Applications"), userHome?.resolve("Applications"))
    .asSequence()
    .map { it.resolve("${appName}.app").resolve("Contents").resolve("MacOS").resolve(knownBrowser.macExecutable) }
}

private fun harmonyOSCandidates(): Sequence<Path> {
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
fun detectBrowserType(browser: WebBrowser): KnownBrowser? {
  return when {
    findInNameAndPath("chromium", browser) -> KnownBrowser.CHROMIUM
    findInNameAndPath("chrome", browser) -> when {
      findInNameAndPath("canary", browser) || findInNameAndPath("sxs", browser) -> KnownBrowser.CHROME_CANARY
      findInNameAndPath("dev", browser) || findInNameAndPath("unstable", browser) -> KnownBrowser.CHROME_DEV
      findInNameAndPath("beta", browser) -> KnownBrowser.CHROME_BETA
      else -> KnownBrowser.CHROME
    }
    findInNameAndPath("edge", browser) -> when {
      findInNameAndPath("canary", browser) || findInNameAndPath("sxs", browser) -> KnownBrowser.EDGE_CANARY
      findInNameAndPath("dev", browser) -> KnownBrowser.EDGE_DEV
      findInNameAndPath("beta", browser) -> KnownBrowser.EDGE_BETA
      else -> KnownBrowser.EDGE
    }
    findInNameAndPath("opera", browser) -> when {
      findInNameAndPath("gx", browser) -> KnownBrowser.OPERA_GX
      findInNameAndPath("developer", browser) -> KnownBrowser.OPERA_DEVELOPER
      findInNameAndPath("beta", browser) -> KnownBrowser.OPERA_BETA
      else -> KnownBrowser.OPERA
    }
    findInNameAndPath("brave", browser) -> when {
      findInNameAndPath("nightly", browser) -> KnownBrowser.BRAVE_NIGHTLY
      findInNameAndPath("beta", browser) -> KnownBrowser.BRAVE_BETA
      else -> KnownBrowser.BRAVE
    }
    findInNameAndPath("vivaldi", browser) -> {
      if (findInNameAndPath("snapshot", browser)) KnownBrowser.VIVALDI_SNAPSHOT else KnownBrowser.VIVALDI
    }
    findInNameAndPath("firefox", browser) -> when {
      findInNameAndPath("nightly", browser) -> KnownBrowser.FIREFOX_NIGHTLY
      findInNameAndPath("developer", browser) -> KnownBrowser.FIREFOX_DEVELOPER_EDITION
      else -> KnownBrowser.FIREFOX
    }
    findInNameAndPath("safari", browser) -> {
      if (findInNameAndPath("technology preview", browser)) KnownBrowser.SAFARI_TECHNOLOGY_PREVIEW else KnownBrowser.SAFARI
    }
    else -> null
  }
}

@ApiStatus.Internal
class BrowserExecutableNotFoundException(browser: WebBrowser) : Exception() {
  @get:NlsContexts.DialogMessage
  override val message: String = IdeBundle.message("dialog.message.could.not.find.browser.executable", browser.name)
}
