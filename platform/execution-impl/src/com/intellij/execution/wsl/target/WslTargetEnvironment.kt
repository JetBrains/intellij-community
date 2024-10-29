// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.target.*
import com.intellij.execution.target.local.toLocalPtyOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslProxy
import com.intellij.execution.wsl.rootMappings
import com.intellij.execution.wsl.runCommand
import com.intellij.execution.wsl.sync.WslSync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.fileSizeSafe
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Path
import java.util.*
import kotlin.Throws

private const val LOCALHOST = "localhost"

private val LOG = logger<WslTargetEnvironment>()

class WslTargetEnvironment(override val request: WslTargetEnvironmentRequest,
                           private val distribution: WSLDistribution) : TargetEnvironment(request), ExternallySynchronized {

  private val myUploadVolumes: MutableMap<UploadRoot, UploadableVolume> = HashMap()
  private val myDownloadVolumes: MutableMap<DownloadRoot, DownloadableVolume> = HashMap()
  private val myTargetPortBindings: MutableMap<TargetPortBinding, ResolvedPortBinding> = HashMap()
  private val myLocalPortBindings: MutableMap<LocalPortBinding, ResolvedPortBinding> = HashMap()
  private val proxies = mutableMapOf<Int, WslProxy>() //port to proxy
  private val remoteDirsToDelete = mutableListOf<String>()

  override val synchronizedVolumes: List<SynchronizedVolume> = distribution.rootMappings.map {
    SynchronizedVolume(Path.of(it.localRoot), it.remoteRoot)
  }

  override val uploadVolumes: Map<UploadRoot, UploadableVolume>
    get() = Collections.unmodifiableMap(myUploadVolumes)
  override val downloadVolumes: Map<DownloadRoot, DownloadableVolume>
    get() = Collections.unmodifiableMap(myDownloadVolumes)
  override val targetPortBindings: Map<TargetPortBinding, ResolvedPortBinding>
    get() = Collections.unmodifiableMap(myTargetPortBindings)
  override val localPortBindings: Map<LocalPortBinding, ResolvedPortBinding>
    get() = Collections.unmodifiableMap(myLocalPortBindings)

  override val targetPlatform: TargetPlatform
    get() = TargetPlatform(Platform.UNIX)

  private val wslIpAddress: InetAddress = distribution.wslIpAddress

  init {
    val useWslSync = (request as? VolumeCopyingRequest)?.shouldCopyVolumes == true && Registry.`is`("ide.wsl.use_wsl_sync")
    for (uploadRoot in request.uploadVolumes) {
      val targetRoot: String? = if (useWslSync) uploadRoot.targetRootPath.remoteRoot
      else toLinuxPath(uploadRoot.localRootPath.toAbsolutePath().toString())
      if (targetRoot != null) {
        myUploadVolumes[uploadRoot] = Volume(uploadRoot.localRootPath, targetRoot, useWslSync)
      }
      else {
        LOG.error("Cannot register upload volume: WSL path not found for local path: " + uploadRoot.localRootPath)
      }
    }
    for (downloadRoot in request.downloadVolumes) {
      val localRootPath = downloadRoot.localRootPath ?: FileUtil.createTempDirectory("intellij-target.", "").toPath()
      val targetRoot: String? = if (useWslSync) downloadRoot.targetRootPath.remoteRoot
      else toLinuxPath(localRootPath.toAbsolutePath().toString())
      if (targetRoot != null) {
        myDownloadVolumes[downloadRoot] = Volume(localRootPath, targetRoot, useWslSync)
      }
    }
    for (targetPortBinding in request.targetPortBindings) {
      val theOnlyPort = targetPortBinding.target
      if (targetPortBinding.local != null && targetPortBinding.local != theOnlyPort) {
        throw UnsupportedOperationException("TCP port forwarding for the local target is not implemented. " +
                                            "Please use the same port number for both local and target ports.")
      }
      myTargetPortBindings[targetPortBinding] = ResolvedPortBinding(localEndpoint = HostPort(wslIpAddress.hostAddress, theOnlyPort),
                                                                    targetEndpoint = HostPort(LOCALHOST, targetPortBinding.target))
    }

    for (localPortBinding in request.localPortBindings) {
      // Ports bound on localhost in Windows can be accessed by linux apps running in WSL1, but not in WSL2:
      // https://docs.microsoft.com/en-US/windows/wsl/compare-versions#accessing-network-applications
      val localPort = localPortBinding.local
      val hostPort = HostPort("127.0.0.1", if (distribution.version > 1) getWslPort(localPort) else localPort)
      myLocalPortBindings[localPortBinding] = ResolvedPortBinding(hostPort, hostPort)
    }
  }

  private fun getWslPort(localPort: Int): Int {
    proxies[localPort]?.wslIngressPort?.let {
      return it
    }
    WslProxy(distribution, localPort).apply {
      proxies[localPort] = this
      return this.wslIngressPort
    }
  }

  private fun toLinuxPath(localPath: String): String? = distribution.getWslPath(localPath)

  private val TargetPath.remoteRoot: String
    get() = when (this) {
      is TargetPath.Temporary -> distribution.runCommand("mktemp", "-d").getOrThrow().also { remoteDirsToDelete += it }
      is TargetPath.Persistent -> absolutePath
    }

  @Throws(ExecutionException::class)
  override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
    val ptyOptions = commandLine.ptyOptions?.toLocalPtyOptions()
    val generalCommandLine = if (ptyOptions != null) {
      PtyCommandLine(commandLine.collectCommandsSynchronously()).withOptions(ptyOptions)
    }
    else {
      GeneralCommandLine(commandLine.collectCommandsSynchronously())
    }
    generalCommandLine.environment.putAll(commandLine.environmentVariables)
    request.wslOptions.remoteWorkingDirectory = commandLine.workingDirectory
    generalCommandLine.withRedirectErrorStream(commandLine.isRedirectErrorStream)
    distribution.patchCommandLine(generalCommandLine, null, request.wslOptions)
    return generalCommandLine.createProcess().apply {
      onExit().whenCompleteAsync { _, _ ->
        proxies.forEach { Disposer.dispose(it.value) }
        proxies.clear()
      }
    }
  }

  override fun shutdown() {
    for (dir in remoteDirsToDelete) {
      try {
        distribution.runCommand("rm", "-rf", dir)
      }
      catch (ex: Exception) {
        LOG.warn(ex)
      }
    }
    remoteDirsToDelete.clear()
  }

  private inner class Volume(override val localRoot: Path,
                             override val targetRoot: String,
                             private val useWslSync: Boolean) : UploadableVolume, DownloadableVolume {
    @Throws(IOException::class)
    override fun resolveTargetPath(relativePath: String): String {
      val localPath = FileUtil.toCanonicalPath(FileUtil.join(localRoot.toString(), relativePath))
      return toLinuxPath(localPath) ?: throw RuntimeException("Cannot find Linux path for $localPath (${distribution.msId})")
    }

    @Throws(IOException::class)
    override fun upload(relativePath: String, targetProgressIndicator: TargetProgressIndicator) {
      if (!useWslSync) return
      WslSync.syncWslFolders("$targetRoot/$relativePath", localRoot.resolve(relativePath), distribution, linToWinCopy = false)
    }

    @Throws(IOException::class)
    override fun download(relativePath: String, progressIndicator: ProgressIndicator) {
      if (useWslSync) {
        WslSync.syncWslFolders("$targetRoot/$relativePath", localRoot.resolve(relativePath), distribution, linToWinCopy = true)
        return
      }
      // synchronization may be slow -- let us wait until file size does not change in a reasonable amount of time
      // (see https://github.com/microsoft/WSL/issues/4197)
      val path = localRoot.resolve(relativePath)
      var previousSize = -2L
      var newSize = path.fileSizeSafe(-1)
      while (previousSize < newSize) {
        Thread.sleep(100)
        previousSize = newSize
        newSize = path.fileSizeSafe(-1)
      }
      if (newSize == -1L) {
        LOG.warn("Path $path was not found on local filesystem")
      }
    }
  }
}
