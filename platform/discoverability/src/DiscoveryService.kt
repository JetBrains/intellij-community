// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.discoverability

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.io.BuiltInServer
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.DefaultPrettyPrinter
import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute
import java.nio.file.attribute.PosixFilePermissions.fromString
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.fileStore

@Service(Service.Level.APP)
internal class DiscoveryService(private val coroutineScope: CoroutineScope) {
  internal companion object {
    private val LOG = logger<DiscoveryService>()
    private const val FILE_SUFFIX = "-ide-instance.json"
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

        val server = serverManager.serverDisposable as? BuiltInServer
        if (server == null) {
          LOG.warn("Built-in server is not available; skipping discovery info")
          ready.completeExceptionally(IllegalStateException("Built-in server is not available"))
          return@launch
        }

        val instanceDir = PathManager.getCommonDataPath().resolve("discovery")
        createDirectories(instanceDir)
        cleanUpStaleFiles(instanceDir)

        val pid = ProcessHandle.current().pid()
        jsonFile = instanceDir.resolve("$pid$FILE_SUFFIX")
        serverAddress = server.address
        serverPort = server.port

        writeInstanceInfo(jsonFile, serverAddress, serverPort)

        ShutDownTracker.getInstance().registerShutdownTask { Files.deleteIfExists(jsonFile) }
        ready.complete(Unit)
      }
      catch (e: Exception) {
        LOG.warn("Failed to write discovery info file", e)
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
            LOG.debug("Cleaned up stale discovery info file: $fileName")
          }
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to clean up stale discovery info files", e)
    }
  }

  private fun writeInstanceInfo(jsonFile: Path, address: InetAddress, port: Int) {
    openOutputStream(jsonFile).use { out ->
      writeDiscoveryInfoJson(out, address, port)
    }
  }

  private fun createDirectories(path: Path) {
    val parent = path.parent
    if (parent != null && supportsPosixPermissions(parent)) {
      val permissions = fromString("rwx------")
      Files.createDirectories(path, asFileAttribute(permissions))
      Files.setPosixFilePermissions(path, permissions) //ensure permissions for already existing directories
    }
    else {
      Files.createDirectories(path)
    }
  }

  private fun openOutputStream(path: Path): OutputStream {
    Files.deleteIfExists(path)
    val parent = path.parent
    if (parent != null && supportsPosixPermissions(parent)) {
      try {
        Files.createFile(path, asFileAttribute(fromString("rw-------")))
        return Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
      }
      catch (e: Exception) {
        Files.deleteIfExists(path)
        throw IllegalStateException("Cannot create $path with owner-only permissions", e)
      }
    }
    return Files.newOutputStream(path)
  }

  private fun supportsPosixPermissions(path: Path): Boolean {
    return try {
      path.fileStore().supportsFileAttributeView("posix")
    }
    catch (e: Exception) {
      LOG.warn("Failed to check support of posix permissions on $path", e)
      false
    }
  }
}

@ApiStatus.Internal
@VisibleForTesting
fun writeDiscoveryInfoJson(out: OutputStream, address: InetAddress, port: Int) {
  val appInfo = ApplicationInfo.getInstance()
  val namesInfo = ApplicationNamesInfo.getInstance()

  JsonFactory().createGenerator(out, DefaultPrettyPrinter()).use { writer ->
    writer.obj {
      writer.writeStringField("url", "http://${address.hostAddress}:$port")
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

      writer.obj("properties") {
        for (contributor in DiscoveryInfoContributor.EP_NAME.extensionsIfPointIsRegistered) {
          contributor.contribute(writer)
        }
      }
    }
  }
}
