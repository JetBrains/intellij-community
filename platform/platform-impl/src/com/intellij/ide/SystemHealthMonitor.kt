// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.UnixProcessManager
import com.intellij.ide.actions.EditCustomVmOptionsAction
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.shellEnvDeferred
import com.intellij.jna.JnaLoader
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SystemProperties
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import com.intellij.util.ui.IoErrorText
import com.sun.jna.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun startSystemHealthMonitor() {
  withContext(Dispatchers.IO) {
    checkInstallationIntegrity()
  }
  checkIdeDirectories()
  withContext(Dispatchers.IO) {
    checkRuntime()
  }
  checkReservedCodeCacheSize()
  checkEnvironment()
  withContext(Dispatchers.IO) {
    checkSignalBlocking()
    checkTempDirEnvVars()
  }
  startDiskSpaceMonitoring()
}

private interface LibC : Library {
  interface Handler : Callback {
    fun callback(sig: Int)

    companion object {
      // ref: java.lang.Terminator
      @JvmField
      val TERMINATE: Handler = object : Handler {
        override fun callback(sig: Int) = exitProcess(128 + sig)
      }
    }
  }

  fun sigaction(sig: Int, action: Pointer?, oldAction: Pointer?): Int
  fun signal(sig: Int, handler: Handler?): Pointer?

  companion object {
    @JvmField
    val SIG_IGN = Pointer(1L)
  }
}

private val LOG = logger<Application>()

private class MyNotification(content: @NlsContexts.NotificationContent String,
                             type: NotificationType,
                             displayId: String?) : Notification(NOTIFICATION_GROUP_ID, content, type), NotificationFullContent {
  init {
    displayId?.let { setDisplayId(it) }
  }
}

private const val NOTIFICATION_GROUP_ID = "System Health"

private fun checkInstallationIntegrity() {
  if (!SystemInfoRt.isUnix || SystemInfoRt.isMac) {
    return
  }

  try {
    Files.list(Path.of(PathManager.getLibPath())).use { stream ->
      // see `LinuxDistributionBuilder#generateVersionMarker`
      val markers = stream.filter { it.fileName.toString().startsWith("build-marker-") }.count()
      if (markers > 1) {
        showNotification(key = "mixed.bag.installation", suppressable = false, action = null,
                         ApplicationNamesInfo.getInstance().fullProductName)
      }
    }
  }
  catch (e: IOException) {
    LOG.warn("${e.javaClass.name}: ${e.message}")
  }
}

private fun checkIdeDirectories() {
  if (System.getProperty(PathManager.PROPERTY_PATHS_SELECTOR) != null) {
    if (System.getProperty(PathManager.PROPERTY_CONFIG_PATH) != null && System.getProperty(PathManager.PROPERTY_PLUGINS_PATH) == null) {
      showNotification(key = "implicit.plugin.directory.path", suppressable = true, action = null, shorten(PathManager.getPluginsPath()))
    }
    if (System.getProperty(PathManager.PROPERTY_SYSTEM_PATH) != null && System.getProperty(PathManager.PROPERTY_LOG_PATH) == null) {
      showNotification(key = "implicit.log.directory.path", suppressable = true, action = null, shorten(PathManager.getLogPath()))
    }
  }
}

private fun shorten(pathStr: String): String {
  val path = Path.of(pathStr).toAbsolutePath()
  val userHome = Path.of(SystemProperties.getUserHome())
  return if (path.startsWith(userHome)) {
    val relative = userHome.relativize(path)
    if (SystemInfoRt.isWindows) "%USERPROFILE%\\$relative" else "~/$relative"
  }
  else {
    pathStr
  }
}

private suspend fun checkRuntime() {
  if (!CpuArch.isEmulated()) {
    return
  }

  LOG.info("${CpuArch.CURRENT} appears to be emulated")
  if (SystemInfoRt.isMac && CpuArch.isIntel64()) {
    val downloadAction = NotificationAction.createSimpleExpiring(IdeBundle.message("bundled.jre.m1.arch.message.download")) {
      BrowserUtil.browse("https://www.jetbrains.com/products/#type=ide")
    }
    showNotification(key = "bundled.jre.m1.arch.message",
                     suppressable = true,
                     action = downloadAction,
                     ApplicationNamesInfo.getInstance().fullProductName)
  }
  var jreHome = SystemProperties.getJavaHome()

  if (PathManager.isUnderHomeDirectory(jreHome) || isModernJBR()) {
    return
  }

  // boot JRE is non-bundled and is either non-JB or older than bundled
  var switchAction: NotificationAction? = null
  val directory = PathManager.getCustomOptionsDirectory()
  if (directory != null && (SystemInfoRt.isWindows || SystemInfoRt.isMac || SystemInfoRt.isLinux) && isJbrOperational()) {
    val scriptName = ApplicationNamesInfo.getInstance().scriptName
    val configName = scriptName + (if (!SystemInfoRt.isWindows) "" else if (CpuArch.isIntel64()) "64.exe" else ".exe") + ".jdk"
    val configFile = Path.of(directory, configName)
    if (Files.isRegularFile(configFile)) {
      switchAction = NotificationAction.createSimpleExpiring(IdeBundle.message("action.SwitchToJBR.text")) {
        try {
          Files.delete(configFile)
          ApplicationManagerEx.getApplicationEx().restart(true)
        }
        catch (e: IOException) {
          LOG.warn("cannot delete $configFile", e)
          val content = IdeBundle.message("cannot.delete.jre.config", configFile, IoErrorText.message(e))
          Notification(NOTIFICATION_GROUP_ID, content, NotificationType.ERROR).notify(null)
        }
      }
    }
  }
  jreHome = jreHome.removeSuffix("/Contents/Home")
  showNotification(key = "bundled.jre.version.message",
                   suppressable = false,
                   action = switchAction,
                   JavaVersion.current(), System.getProperty("java.vendor"), jreHome)
}

// when can't detect a JBR version, give a user the benefit of the doubt
private fun isModernJBR(): Boolean {
  if (!SystemInfo.isJetBrainsJvm) {
    return false
  }

  // when can't detect a JBR version, give a user the benefit of the doubt
  val jbrVersion = JdkVersionDetector.getInstance().detectJdkVersionInfo(PathManager.getBundledRuntimePath())
  return jbrVersion == null || JavaVersion.current() >= jbrVersion.version
}

private suspend fun isJbrOperational(): Boolean {
  val bin = Path.of(PathManager.getBundledRuntimePath(), if (SystemInfoRt.isWindows) "bin/java.exe" else "bin/java")
  if (Files.isRegularFile(bin) && (SystemInfoRt.isWindows || Files.isExecutable(bin))) {
    try {
      return withTimeout(30.seconds) {
        ProcessBuilder(bin.toString(), "-version")
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .start()
          .onExit()
          .asDeferred()
          .await()
          .exitValue() == 0
      }
    }
    catch (e: ExecutionException) {
      LOG.debug(e)
    }
  }
  return false
}

private fun checkReservedCodeCacheSize() {
  val reservedCodeCacheSize = VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, true)
  val minReservedCodeCacheSize = if (PluginManagerCore.isRunningFromSources()) 240 else 512
  if (reservedCodeCacheSize in 1 until minReservedCodeCacheSize) {
    val vmEditAction = EditCustomVmOptionsAction()
    val action = if (vmEditAction.isEnabled()) {
      NotificationAction.createExpiring(IdeBundle.message("vm.options.edit.action.cap")) { e, _ -> vmEditAction.actionPerformed(e!!) }
    }
    else {
      null
    }
    showNotification("code.cache.warn.message", true, action, reservedCodeCacheSize, minReservedCodeCacheSize)
  }
}

private suspend fun checkEnvironment() {
  val usedVars = sequenceOf("_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS")
    .filter { `var` -> !System.getenv(`var`).isNullOrEmpty() }
    .toList()
  if (!usedVars.isEmpty()) {
    showNotification("vm.options.env.vars", true, null, usedVars.joinToString(separator = ", "))
  }

  try {
    if (shellEnvDeferred!!.await() == false) {
      val action = NotificationAction.createSimpleExpiring(IdeBundle.message("shell.env.loading.learn.more")) {
        BrowserUtil.browse("https://intellij.com/shell-env")
      }
      val appName = ApplicationNamesInfo.getInstance().fullProductName
      val shell = System.getenv("SHELL")
      showNotification("shell.env.loading.failed", true, action, appName, shell)
    }
  }
  catch (e: Exception) {
    LOG.error(e)
  }
}

private fun checkSignalBlocking() {
  if (!SystemInfoRt.isUnix || !JnaLoader.isLoaded()) {
    return
  }

  try {
    val sa = Memory(256)
    val libC = Native.load("c", LibC::class.java)
    if (libC.sigaction(UnixProcessManager.SIGINT, Pointer.NULL, sa) == 0 && LibC.SIG_IGN == sa.getPointer(0)) {
      libC.signal(UnixProcessManager.SIGINT, LibC.Handler.TERMINATE)
      LOG.info("restored ignored INT handler")
    }
  }
  catch (e: Throwable) {
    LOG.warn(e)
  }
}

private fun checkTempDirEnvVars() {
  val envVars = if (SystemInfoRt.isWindows) sequenceOf("TMP", "TEMP") else sequenceOf("TMPDIR")
  for (name in envVars) {
    val value = System.getenv(name) ?: continue
    try {
      if (!Files.isDirectory(Path.of(value))) {
        showNotification(key = "temp.dir.env.invalid", suppressable = false, action = null, name, value)
      }
    }
    catch (e: Exception) {
      LOG.warn(e)
      showNotification(key = "temp.dir.env.invalid", suppressable = false, action = null, name, value)
    }
  }
}

private fun showNotification(key: @PropertyKey(resourceBundle = "messages.IdeBundle") String?,
                             suppressable: Boolean,
                             action: NotificationAction?,
                             vararg params: Any) {
  if (suppressable) {
    val ignored = PropertiesComponent.getInstance().isValueSet("ignore.$key")
    LOG.warn("issue detected: $key${if (ignored) " (ignored)" else ""}")
    if (ignored) {
      return
    }
  }

  val notification = MyNotification(IdeBundle.message(key!!, *params), NotificationType.WARNING, key)
  if (action != null) {
    notification.addAction(action)
  }
  if (suppressable) {
    notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("sys.health.acknowledge.action")) {
      PropertiesComponent.getInstance().setValue("ignore.$key", "true")
    })
  }
  notification.isImportant = true
  notification.isSuggestionType = true
  Notifications.Bus.notify(notification)
}


private const val NO_DISK_SPACE_THRESHOLD = (1 shl 20).toLong()
private const val LOW_DISK_SPACE_THRESHOLD = (50 shl 20).toLong()
// 500 MB/s is (somewhat outdated) peak SSD write speed
private const val MAX_WRITE_SPEED_IN_BPS = (500 shl 20).toLong()

private fun startDiskSpaceMonitoring() {
  if (SystemProperties.getBooleanProperty("idea.no.system.path.space.monitoring", false)) {
    return
  }

  val dir: Path
  val store: FileStore
  try {
    dir = Path.of(PathManager.getSystemPath())
    store = Files.getFileStore(dir)
  }
  catch (e: IOException) {
    LOG.error(e)
    return
  }
  catch (e: InvalidPathException) {
    LOG.error(e)
    return
  }

  @Suppress("DEPRECATION")
  monitorDiskSpace(ApplicationManager.getApplication().coroutineScope, dir, store, initialDelay = 1.seconds)
}

private fun monitorDiskSpace(scope: CoroutineScope, dir: Path, store: FileStore, initialDelay: Duration) {
  scope.launch {
    delay(initialDelay)

    while (isActive) {
      val usableSpace = withContext(Dispatchers.IO) {
        if (Files.exists(dir)) {
          store.usableSpace
        }
        else {
          MAX_WRITE_SPEED_IN_BPS * 60
        }
      }

      if (usableSpace < NO_DISK_SPACE_THRESHOLD) {
        LOG.warn("Extremely low disk space: $usableSpace")
        withContext(Dispatchers.EDT) {
          Messages.showErrorDialog(IdeBundle.message("no.disk.space.message", store.name()), IdeBundle.message("no.disk.space.title"))
        }

        delay(5.seconds)
      }
      else if (usableSpace < LOW_DISK_SPACE_THRESHOLD) {
        LOG.warn("Low disk space: $usableSpace")
        MyNotification(IdeBundle.message("low.disk.space.message", store.name()), NotificationType.WARNING, "low.disk")
          .setTitle(IdeBundle.message("low.disk.space.title"))
          .whenExpired { monitorDiskSpace(scope, dir, store, initialDelay = 5.seconds) }
          .notify(null)
        return@launch
      }
      else {
        delay(((usableSpace - LOW_DISK_SPACE_THRESHOLD) / MAX_WRITE_SPEED_IN_BPS).coerceIn(5, 3600).seconds)
      }
    }
  }
}
