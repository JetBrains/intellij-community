// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.eel

import com.intellij.execution.Platform
import com.intellij.execution.eel.EelPathUtils
import com.intellij.execution.target.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.getOrThrow
import com.intellij.platform.ijent.tunnels.forwardLocalPort
import com.intellij.platform.util.coroutines.channel.ChannelInputStream
import com.intellij.platform.util.coroutines.channel.ChannelOutputStream
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.io.copyToAsync
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private fun EelPlatform.toTargetPlatform(): TargetPlatform = when (this) {
  is EelPlatform.Posix -> TargetPlatform(Platform.UNIX)
  is EelPlatform.Windows -> TargetPlatform(Platform.WINDOWS)
}

private fun LocalHostPort(port: Int) = HostPort("localhost", port)

@ApiStatus.Internal
class EelTargetEnvironmentRequest(override val configuration: Configuration) : BaseTargetEnvironmentRequest(), VolumeCopyingRequest {
  class Configuration(val eel: EelApi) : TargetEnvironmentConfiguration("eel"), TargetConfigurationWithLocalFsAccess {
    override var projectRootOnTarget: String = ""
    override val asTargetConfig: TargetEnvironmentConfiguration = this

    override fun getTargetPathIfLocalPathIsOnTarget(probablyPathOnTarget: Path): FullPathOnTarget? {
      return eel.mapper.getOriginalPath(probablyPathOnTarget)?.toString()
    }
  }

  override val targetPlatform: TargetPlatform = configuration.eel.platform.toTargetPlatform()

  override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment {
    return EelTargetEnvironment(this)
  }

  override var shouldCopyVolumes: Boolean = false
}

private class EelTargetEnvironment(override val request: EelTargetEnvironmentRequest) : TargetEnvironment(request) {
  private val myUploadVolumes: MutableMap<UploadRoot, UploadableVolume> = HashMap()
  private val myDownloadVolumes: MutableMap<DownloadRoot, DownloadableVolume> = HashMap()
  private val myTargetPortBindings: MutableMap<TargetPortBinding, ResolvedPortBinding> = HashMap()
  private val myLocalPortBindings: MutableMap<LocalPortBinding, ResolvedPortBinding> = ConcurrentHashMap()

  private val eel = request.configuration.eel

  private val forwardingScope by lazy { service<EelTargetScope>().scope.childScope("Eel target forwarding scope: ${request.configuration.eel}") }

  override val uploadVolumes: Map<UploadRoot, UploadableVolume>
    get() = Collections.unmodifiableMap(myUploadVolumes)
  override val downloadVolumes: Map<DownloadRoot, DownloadableVolume>
    get() = Collections.unmodifiableMap(myDownloadVolumes)
  override val targetPortBindings: Map<TargetPortBinding, ResolvedPortBinding>
    get() = Collections.unmodifiableMap(myTargetPortBindings)
  override val localPortBindings: Map<LocalPortBinding, ResolvedPortBinding>
    get() = Collections.unmodifiableMap(myLocalPortBindings)

  init {
    request.uploadVolumes.forEach { uploadRoot ->
      myUploadVolumes[uploadRoot] = EelVolume.createFor(eel, uploadRoot)
    }

    request.downloadVolumes.forEach { downloadRoot ->
      myDownloadVolumes[downloadRoot] = EelVolume.createFor(eel, downloadRoot)
    }

    request.targetPortBindings.forEach { targetPortBinding ->
      val localPort = targetPortBinding.local ?: NetUtils.findAvailableSocketPort()
      val targetAddress = eel.tunnels.hostAddressBuilder(targetPortBinding.target.toUShort()).build()

      forwardingScope.launch {
        forwardLocalPort(eel.tunnels, localPort, targetAddress)
      }

      myTargetPortBindings[targetPortBinding] = ResolvedPortBinding(
        localEndpoint = HostPort(NetUtils.getLocalHostString(), localPort),
        targetEndpoint = LocalHostPort(targetPortBinding.target)
      )
    }

    request.localPortBindings.forEach { localPortBinding ->
      forwardingScope.launch {
        val remoteAddress = eel.tunnels.hostAddressBuilder((localPortBinding.target ?: 0).toUShort()).build()
        val acceptor = eel.tunnels.getAcceptorForRemotePort(remoteAddress).getOrThrow()

        val socket = Socket()

        socket.connect(InetSocketAddress(localPortBinding.local))

        launch {
          val connection = acceptor.incomingConnections.receive()

          launch {
            socket.getInputStream().copyToAsync(ChannelOutputStream.forByteBuffers(connection.sendChannel))
          }

          launch {
            ChannelInputStream.forByteBuffers(this, connection.receiveChannel).copyToAsync(socket.getOutputStream())
          }
        }

        awaitCancellationAndInvoke {
          acceptor.close()
          socket.close()
        }

        myLocalPortBindings[localPortBinding] = ResolvedPortBinding(
          localEndpoint = LocalHostPort(localPortBinding.local),
          targetEndpoint = LocalHostPort(acceptor.boundAddress.port.toInt())
        )
      }
    }
  }

  private class EelVolume private constructor(
    private val eel: EelApi,
    override val localRoot: Path,
    override val targetRoot: String,
  ) : UploadableVolume, DownloadableVolume {
    private fun targetRootPath(): Path {
      return eel.mapper.toNioPath(eel.fs.getPath(targetRoot).getOrThrow())
    }

    override fun upload(relativePath: String, targetProgressIndicator: TargetProgressIndicator) {
      val from = localRoot.resolve(relativePath).normalize()
      val to = targetRootPath().resolve(relativePath).normalize()
      if (from == to) return
      EelPathUtils.walkingTransfer(from, to, false) // TODO: generalize com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider.copy
    }

    override fun download(relativePath: String, progressIndicator: ProgressIndicator) {
      val from = targetRootPath().resolve(relativePath).normalize()
      val to = localRoot.resolve(relativePath).normalize()
      if (from == to) return
      EelPathUtils.walkingTransfer(from, to, false) // TODO: generalize com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider.copy
    }

    override fun resolveTargetPath(relativePath: String): String {
      return eel.mapper.getOriginalPath(targetRootPath().resolve(relativePath))!!.toString()
    }

    companion object {
      private fun createFor(eel: EelApi, localPathGetter: () -> Path, targetPathGetter: () -> TargetPath): EelVolume {
        val localRootPath = localPathGetter()

        val remoteRoot = when (val targetRootPath = targetPathGetter()) {
          is TargetPath.Temporary -> {
            eel.mapper.getOriginalPath(localRootPath)?.toString() ?: runBlockingCancellable {
              val options = EelFileSystemApi.createTemporaryDirectoryOptions()

              targetRootPath.prefix?.let(options::prefix)
              targetRootPath.parentDirectory?.let(eel.fs::getPath)?.getOrThrow()?.let(options::parentDirectory)
              options.deleteOnExit(true)

              eel.fs.createTemporaryDirectory(options).toString()
            }
          }
          is TargetPath.Persistent -> targetRootPath.absolutePath
        }

        return EelVolume(eel, localRootPath, remoteRoot)
      }

      fun createFor(eel: EelApi, uploadRoot: UploadRoot): EelVolume {
        return createFor(eel, { uploadRoot.localRootPath }, { uploadRoot.targetRootPath })
      }

      fun createFor(eel: EelApi, downloadRoot: DownloadRoot): EelVolume {
        val target = downloadRoot.targetRootPath as TargetPath.Persistent // how could it be temp?

        if (downloadRoot.localRootPath == null) {
          return EelVolume(
            eel = eel,
            localRoot = FileUtil.createTempDirectory("intellij-eel-target.", "").toPath(),
            targetRoot = target.absolutePath,
          )
        }
        else {
          return createFor(eel, { downloadRoot.localRootPath!! }, { downloadRoot.targetRootPath })
        }
      }
    }
  }

  override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
    val command = commandLine.collectCommandsSynchronously()
    val builder = EelExecApi.executeProcessBuilder(command.first())

    builder.args(command.drop(1))
    builder.env(commandLine.environmentVariables)
    builder.workingDirectory(commandLine.workingDirectory)

    return runBlockingCancellable { eel.exec.execute(builder).getOrThrow().convertToJavaProcess() }
  }

  override val targetPlatform: TargetPlatform = request.targetPlatform

  override fun shutdown() {
    forwardingScope.cancel()
  }
}

@Service
private class EelTargetScope(val scope: CoroutineScope)