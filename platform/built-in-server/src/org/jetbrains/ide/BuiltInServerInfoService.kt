// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.SpecialConfigFiles
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.io.jackson.createGenerator
import com.intellij.util.io.jackson.obj
import com.intellij.util.io.jackson.writeNumberField
import com.intellij.util.io.jackson.writeStringField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.DefaultPrettyPrinter
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.readText

private val LOG = logger<BuiltInServerDiscoveryService>()
private const val FILE_SUFFIX = "-build-in-server.json"

@Service(Service.Level.APP)
internal class BuiltInServerDiscoveryService(private val coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): BuiltInServerDiscoveryService = service()
  }

  private val ready = CompletableDeferred<Unit>()
  private lateinit var jsonFile: Path
  private lateinit var serverAddress: InetAddress
  private var serverPort: Int = 0
  private val writeMutex = Mutex()

  init {
    coroutineScope.launch(Dispatchers.IO) {
      try {
        val serverManager = BuiltInServerManager.getInstance()
        serverManager.waitForStart()

        val instanceDir = Path.of(System.getProperty("user.home"), ".intellij")
        Files.createDirectories(instanceDir)
        cleanUpStaleFiles(instanceDir)

        val pid = ProcessHandle.current().pid()
        jsonFile = instanceDir.resolve("$pid$FILE_SUFFIX")
        serverAddress = serverManager.address
        serverPort = serverManager.port

        writeInstanceInfo(jsonFile, serverAddress, serverPort)

        ShutDownTracker.getInstance().registerShutdownTask { Files.deleteIfExists(jsonFile) }
        ready.complete(Unit)
      }
      catch (e: Exception) {
        LOG.warn("Failed to write server-state file", e)
        ready.completeExceptionally(e)
      }
    }
  }

  fun scheduleNotifyUpdate() {
    coroutineScope.launch {
      notifyUpdate()
    }
  }

  suspend fun notifyUpdate() {
    try {
      ready.await()
    }
    catch (_: Exception) {
      return
    }
    writeMutex.withLock {
      withContext(Dispatchers.IO) {
        writeInstanceInfo(jsonFile, serverAddress, serverPort)
      }
    }
  }

  private fun cleanUpStaleFiles(systemDir: Path) {
    try {
      Files.newDirectoryStream(systemDir, "*$FILE_SUFFIX").use { stream ->
        for (file in stream) {
          val fileName = file.fileName.toString()
          val pidStr = fileName.removeSuffix(FILE_SUFFIX)
          val pid = pidStr.toLongOrNull() ?: continue
          if (ProcessHandle.of(pid).isEmpty) {
            Files.deleteIfExists(file)
            LOG.debug("Cleaned up stale server-state file: $fileName")
          }
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to clean up stale server-state files", e)
    }
  }

  private fun writeInstanceInfo(jsonFile: Path, address: InetAddress, port: Int) {
    val authToken = readAuthToken()
    val appInfo = ApplicationInfo.getInstance()
    val namesInfo = ApplicationNamesInfo.getInstance()

    Files.newOutputStream(jsonFile).use { out ->
      JsonFactory().createGenerator(out, DefaultPrettyPrinter()).use { writer ->
        writer.obj {
          writer.writeStringField("url", "http://${address.hostAddress}:$port")
          writer.writeStringField("authToken", authToken)
          writer.writeStringField("dateTime", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
          writer.writeNumberField("pid", ProcessHandle.current().pid())

          val vmOptionsFile = System.getProperty("jb.vmOptionsFile")
          if (vmOptionsFile != null) {
            writer.writeStringField("jvmOptionsPath", Path.of(vmOptionsFile).toAbsolutePath().toString())
          }
          else {
            writer.writeStringField("jvmOptionsPath", null)
          }

          writer.obj("paths") {
            writer.writeStringField("installDir", PathManager.getHomePath())
            writer.writeStringField("bin", PathManager.getBinPath())
            writer.writeStringField("config", PathManager.getConfigDir().toString())
            writer.writeStringField("system", PathManager.getSystemDir().toString())
            writer.writeStringField("logs", PathManager.getLogDir().toString())
            writer.writeStringField("plugins", PathManager.getPluginsDir().toString())
          }

          writer.obj("ideInfo") {
            writer.writeStringField("productCode", appInfo.build.productCode)
            writer.writeStringField("productName", namesInfo.fullProductName)
            writer.writeStringField("fullVersion", appInfo.fullVersion)
            writer.writeStringField("buildNumber", appInfo.build.asString())

            val buildDate = appInfo.buildDate
            if (buildDate != null) {
              val zdt = buildDate.toInstant().atZone(buildDate.timeZone.toZoneId())
              writer.writeStringField("buildDate", DateTimeFormatter.ofPattern("MMMM d, yyyy").format(zdt))
            }
            else {
              writer.writeStringField("buildDate", null)
            }

            writer.obj("runtime") {
              writer.writeStringField("version", System.getProperty("java.runtime.version", ""))
              writer.writeStringField("vmName", System.getProperty("java.vm.name", ""))
              writer.writeStringField("vmVendor", System.getProperty("java.vm.vendor", ""))
            }

            writer.obj("os") {
              writer.writeStringField("name", System.getProperty("os.name", ""))
              writer.writeStringField("version", System.getProperty("os.version", ""))
              writer.writeStringField("arch", System.getProperty("os.arch", ""))
            }

            val gcNames = ManagementFactory.getGarbageCollectorMXBeans().joinToString(", ") { it.name }
            writer.writeStringField("gc", gcNames)
            writer.writeNumberField("memoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024))
          }

          BuiltInServerInfoContributor.EP_NAME.forEachExtensionSafe { contributor ->
            contributor.contribute(writer)
          }
        }
      }
    }
  }

  private fun readAuthToken(): String {
    val tokenFile = Path.of(PathManager.getConfigPath(), SpecialConfigFiles.USER_WEB_TOKEN)
    if (tokenFile.exists()) {
      try {
        return tokenFile.readText().trim()
      }
      catch (e: Exception) {
        LOG.warn("Failed to read auth token", e)
      }
    }
    return ""
  }
}

internal class BuiltInServerDiscoveryServiceLauncher : ApplicationActivity {
  override suspend fun execute() {
    serviceAsync<BuiltInServerDiscoveryService>()
  }
}
