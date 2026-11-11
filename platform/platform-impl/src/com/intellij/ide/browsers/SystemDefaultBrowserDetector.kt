// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.browsers.SystemDefaultBrowserDetector.Companion.MAX_AGE
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.NSWorkspace
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.intellij.util.system.WindowsRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Keeps the known browser that [SystemDefaultBrowser] opens, see [detectSystemDefaultBrowser].
 *
 * [knownBrowser] does not wait for a detection. It returns the last result, and starts a detection in the background
 * when the result is older than [MAX_AGE].
 * The user can change the default browser outside the IDE. So an activation of the IDE marks the result as old,
 * and the next request detects again. Until that detection ends, the request gets the previous result.
 *
 * A remote development backend does not detect, and [knownBrowser] is always `null` there.
 * The client opens the page, and the backend cannot know the default browser of the client.
 */
@Service(Service.Level.APP)
internal class SystemDefaultBrowserDetector(private val coroutineScope: CoroutineScope) {
  @Volatile
  private var detected: KnownBrowser? = null

  @Volatile
  private var detectedAt: TimeSource.Monotonic.ValueTimeMark? = null

  private val isDetecting = AtomicBoolean()

  init {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          if (isOlderThan(MIN_AGE_ON_ACTIVATION)) {
            detectedAt = null
          }
        }
      })
  }

  /** The known browser of the last detection, or `null` before the first detection ends or for an unknown browser. */
  val knownBrowser: KnownBrowser?
    get() {
      detectIfOlderThan(MAX_AGE)
      return detected
    }

  /**
   * Returns the known browser as [knownBrowser] does, but detects it on the calling thread when the result is older than [MAX_AGE].
   * It can wait for a process, so call it on a background thread.
   */
  fun detectKnownBrowser(): KnownBrowser? {
    if (IdeProductMode.isBackend) {
      return null
    }
    return if (isOlderThan(MAX_AGE)) detectAndStore() else detected
  }

  private fun detectIfOlderThan(age: Duration) {
    if (IdeProductMode.isBackend || !isOlderThan(age)) {
      return
    }
    if (!isDetecting.compareAndSet(false, true)) {
      return
    }
    coroutineScope.launch(Dispatchers.IO) {
      try {
        detectAndStore()
      }
      finally {
        isDetecting.set(false)
      }
    }
  }

  private fun isOlderThan(age: Duration): Boolean {
    val at = detectedAt
    return at == null || at.elapsedNow() >= age
  }

  private fun detectAndStore(): KnownBrowser? {
    val result = try {
      detectSystemDefaultBrowser()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.info("Cannot detect the OS default browser", e)
      null
    }
    detected = result
    detectedAt = TimeSource.Monotonic.markNow()
    return result
  }

  companion object {
    private val LOG = logger<SystemDefaultBrowserDetector>()

    private val MAX_AGE: Duration = 10.minutes

    /** An activation marks the result as old only after this time, so a quick switch between the windows keeps the result. */
    private val MIN_AGE_ON_ACTIVATION: Duration = 10.seconds

    @JvmStatic
    fun getInstance(): SystemDefaultBrowserDetector = service()

    /** The icon of [knownBrowser], as for a browser of the list with the name and the path of [knownBrowser]. */
    @JvmStatic
    @OptIn(LowLevelLocalMachineAccess::class) // the icon names the browser of the client OS
    fun iconOf(knownBrowser: KnownBrowser): Icon {
      val path = when (OS.CURRENT) {
        OS.Windows -> knownBrowser.windowsExecutable
        OS.macOS -> knownBrowser.macAppName
        else -> knownBrowser.linuxExecutables.firstOrNull()
      }
      return ConfigurableWebBrowser(UUID.randomUUID(), knownBrowser.family, knownBrowser.macAppName, path, true, null).icon
    }

    /**
     * Returns `true` when [browser] opens a page as [SystemDefaultBrowser] does, because it is [knownBrowser] with the default settings.
     * A browser with its own settings, such as command line options, opens a page in another way.
     */
    @JvmStatic
    fun opensSameAs(browser: WebBrowser, knownBrowser: KnownBrowser): Boolean {
      val family = browser.family ?: return false
      return family == knownBrowser.family &&
             detectBrowserType(browser) == knownBrowser &&
             browser.specificSettings == family.createBrowserSpecificSettings()
    }
  }
}

/**
 * Returns the known browser that the operating system opens for an `https` URL, or `null` for another browser.
 * It reads the system settings or runs a process, so call it on a background thread.
 */
@OptIn(LowLevelLocalMachineAccess::class) // the client OS opens the page, not the OS of the project
private fun detectSystemDefaultBrowser(): KnownBrowser? = when (OS.CURRENT) {
  OS.macOS -> macDefaultBrowserApp()?.let(::knownBrowserOfMacApp)
  OS.Windows -> windowsDefaultBrowser()
  OS.Linux, OS.FreeBSD -> linuxDefaultBrowserDesktopEntry()?.let(::knownBrowserOfDesktopEntry)
  else -> null
}

/** Returns the path of the app bundle that opens an `https` URL, such as `/Applications/Google Chrome.app`. */
private fun macDefaultBrowserApp(): String? {
  if (!Foundation.isAvailable()) {
    return null
  }
  val pool = Foundation.NSAutoreleasePool()
  try {
    val url = Foundation.invoke("NSURL", "URLWithString:", Foundation.nsString("https://www.jetbrains.com"))
    val appUrl = Foundation.invoke(NSWorkspace.getInstance(), "URLForApplicationToOpenURL:", url)
    return if (Foundation.isNil(appUrl)) null else Foundation.toStringViaUTF8(Foundation.invoke(appUrl, "path"))
  }
  finally {
    pool.drain()
  }
}

/** Returns the known browser of the app bundle at [appPath], see [KnownBrowser.macAppName]. */
internal fun knownBrowserOfMacApp(appPath: String): KnownBrowser? {
  val appName = appPath.trimEnd('/').substringAfterLast('/').removeSuffix(".app")
  return KnownBrowser.entries.find { it.macAppName.equals(appName, ignoreCase = true) } ?: detectBrowserType(appName)
}

private const val WINDOWS_USER_CHOICE_KEY = """Software\Microsoft\Windows\Shell\Associations\UrlAssociations\https\UserChoice"""

/** Reads the ProgId that opens an `https` URL, and the command of the ProgId. */
private fun windowsDefaultBrowser(): KnownBrowser? {
  val progId = WindowsRegistry.getString(WindowsRegistry.Hive.CURRENT_USER, WINDOWS_USER_CHOICE_KEY, "ProgId") ?: return null
  val commandKey = """Software\Classes\$progId\shell\open\command"""
  val command = sequenceOf(WindowsRegistry.Hive.CURRENT_USER, WindowsRegistry.Hive.LOCAL_MACHINE)
    .firstNotNullOfOrNull { WindowsRegistry.getString(it, commandKey, "") }
  return knownBrowserOfWindowsProgId(progId, command)
}

/**
 * Returns the known browser of a ProgId, such as `ChromeHTML`, and its [command].
 * The executable of the command identifies the browser, see [KnownBrowser.windowsExecutable].
 * Without a match, the ProgId itself names the browser.
 */
internal fun knownBrowserOfWindowsProgId(progId: String, command: String?): KnownBrowser? {
  val executable = command?.let(::executableOfWindowsCommand)?.replace('/', '\\')
  if (executable != null) {
    val known = KnownBrowser.entries.find { known ->
      val windowsExecutable = known.windowsExecutable
      windowsExecutable != null && executable.endsWith("\\$windowsExecutable", ignoreCase = true)
    }
    if (known != null) {
      return known
    }
  }
  return detectBrowserType(progId)
}

private fun executableOfWindowsCommand(command: String): String {
  val trimmed = command.trim()
  if (trimmed.startsWith('"')) {
    return trimmed.substring(1).substringBefore('"')
  }
  val end = trimmed.indexOf(".exe", ignoreCase = true)
  return if (end >= 0) trimmed.substring(0, end + ".exe".length) else trimmed.substringBefore(' ')
}

private const val LINUX_COMMAND_TIMEOUT_MS = 5_000

/** Returns the desktop entry that opens an `https` URL, such as `google-chrome.desktop`. */
private fun linuxDefaultBrowserDesktopEntry(): String? {
  val commands = listOf(
    listOf("xdg-settings", "get", "default-web-browser"),
    listOf("xdg-mime", "query", "default", "x-scheme-handler/https"),
  )
  for (command in commands) {
    if (!PathEnvironmentVariableUtil.isOnPath(command.first())) {
      continue
    }
    val output = ExecUtil.execAndGetOutput(GeneralCommandLine(command), LINUX_COMMAND_TIMEOUT_MS)
    val entry = output.stdout.trim()
    if (output.exitCode == 0 && !output.isTimeout && entry.isNotEmpty()) {
      return entry
    }
  }
  return null
}

/**
 * Returns the known browser of a desktop entry, such as `google-chrome.desktop` or `org.mozilla.firefox.desktop`.
 * The entry can have the name of the executable, see [KnownBrowser.linuxExecutables]. Otherwise, the entry names the browser.
 */
internal fun knownBrowserOfDesktopEntry(entry: String): KnownBrowser? {
  val id = entry.trim().removeSuffix(".desktop")
  return KnownBrowser.entries.find { id in it.linuxExecutables } ?: detectBrowserType(id)
}
