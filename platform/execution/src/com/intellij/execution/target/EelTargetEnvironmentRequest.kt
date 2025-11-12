// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.platform.eel.provider.utils.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.icons.EMPTY_ICON
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlin.io.path.Path
import kotlin.io.path.isSameFileAs

private fun EelOsFamily.toTargetPlatform(): TargetPlatform = when (this) {
  EelOsFamily.Posix -> TargetPlatform(Platform.UNIX)
  EelOsFamily.Windows -> TargetPlatform(Platform.WINDOWS)
}

private fun LocalHostPort(port: Int) = HostPort("localhost", port)

@NlsSafe
private const val TARGET_TYPE_NAME = "eel"

@ApiStatus.Internal
class EelTargetType : TargetEnvironmentType<EelTargetEnvironmentRequest.Configuration>(TARGET_TYPE_NAME) {

  override val displayName: String = TARGET_TYPE_NAME
  override val icon: Icon = EMPTY_ICON

  override fun isSystemCompatible(): Boolean = false

  override fun createEnvironmentRequest(project: Project?, config: EelTargetEnvironmentRequest.Configuration): TargetEnvironmentRequest {
    return EelTargetEnvironmentRequest(config)
  }

  override fun createConfigurable(
    project: Project,
    config: EelTargetEnvironmentRequest.Configuration,
    defaultLanguage: LanguageRuntimeType<*>?,
    parentConfigurable: Configurable?,
  ): Configurable {
    return object : Configurable {
      override fun createComponent() = null
      override fun isModified(): Boolean = false
      override fun apply() {}
      override fun getDisplayName() = TARGET_TYPE_NAME
    }
  }

  override fun createSerializer(config: EelTargetEnvironmentRequest.Configuration): PersistentStateComponent<*> {
    return config
  }

  override fun createDefaultConfig(): EelTargetEnvironmentRequest.Configuration {
    return EelTargetEnvironmentRequest.Configuration()
  }

  override fun duplicateConfig(config: EelTargetEnvironmentRequest.Configuration): EelTargetEnvironmentRequest.Configuration {
    return EelTargetEnvironmentRequest.Configuration.create(config.descriptor).also {
      it.projectRootOnTarget = config.projectRootOnTarget
    }
  }
}

@ApiStatus.Internal
class EelTargetEnvironmentRequest(
  override val configuration: Configuration,
) : BaseTargetEnvironmentRequest(), VolumeCopyingRequest {
  class Configuration private constructor(
    eelDescriptor: EelDescriptor?,
  ) : TargetEnvironmentConfiguration(TARGET_TYPE_NAME), TargetConfigurationWithLocalFsAccess, PersistentStateComponent<Configuration.PersistentState> {
    internal constructor() : this(null)

    constructor(eelApi: EelApi) : this(eelApi.descriptor)

    private var myDescriptor = eelDescriptor

    val descriptor: EelDescriptor get() = myDescriptor ?: error("EEL descriptor is not set")

    companion object {
      @JvmStatic
      fun create(eelDescriptor: EelDescriptor): Configuration = Configuration(
        eelDescriptor = eelDescriptor
      )
    }

    override var projectRootOnTarget: String = ""
    override val asTargetConfig: TargetEnvironmentConfiguration = this

    override fun getTargetPathIfLocalPathIsOnTarget(probablyPathOnTarget: Path): FullPathOnTarget? {
      return probablyPathOnTarget.asEelPath().takeIf { it.descriptor == descriptor }?.toString()
    }

    override fun getState(): PersistentState {
      return PersistentState().also {
        it.projectRootOnTarget = projectRootOnTarget
        it.eelRootPath = (descriptor as? EelPathBoundDescriptor)?.rootPath.toString()
      }
    }

    override fun loadState(state: PersistentState) {
      projectRootOnTarget = state.projectRootOnTarget ?: ""
      myDescriptor = state.eelRootPath?.let(::Path)?.getEelDescriptor() ?: descriptor
    }

    class PersistentState : BaseState() {
      var projectRootOnTarget: String? by string()
      var eelRootPath: String? by string()
    }
  }

  override val targetPlatform: TargetPlatform = configuration.descriptor.osFamily.toTargetPlatform()

  override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment {
    val env = EelTargetEnvironment(this)
    environmentPrepared(env, progressIndicator)
    return env
  }

  override var shouldCopyVolumes: Boolean = false
}

@ApiStatus.Internal
class EelTargetEnvironment(override val request: EelTargetEnvironmentRequest) : TargetEnvironment(request) {
  private val myUploadVolumes: MutableMap<UploadRoot, UploadableVolume> = HashMap()
  private val myDownloadVolumes: MutableMap<DownloadRoot, DownloadableVolume> = HashMap()
  private val myTargetPortBindings: MutableMap<TargetPortBinding, ResolvedPortBinding> = HashMap()
  private val myLocalPortBindings: MutableMap<LocalPortBinding, ResolvedPortBinding> = ConcurrentHashMap()

  private val eel = request.configuration.descriptor.toEelApiBlocking()

  private val forwardingScope by lazy { service<EelTargetScope>().scope.childScope("Eel target forwarding scope: ${request.configuration.descriptor}") }

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
      val targetAddress = EelTunnelsApi.HostAddress.Builder(targetPortBinding.target.toUShort()).build()

      forwardingScope.forwardLocalPort(eel.tunnels, localPort, targetAddress)

      myTargetPortBindings[targetPortBinding] = ResolvedPortBinding(
        localEndpoint = HostPort(NetUtils.getLocalHostString(), localPort),
        targetEndpoint = LocalHostPort(targetPortBinding.target)
      )
    }

    request.localPortBindings.forEach { localPortBinding ->
      val acceptor = runBlockingMaybeCancellable {
        eel.tunnels.getAcceptorForRemotePort().port((localPortBinding.target ?: 0).toUShort()).eelIt()
      }

      @OptIn(DelicateCoroutinesApi::class, IntellijInternalApi::class)
      forwardingScope.launch(blockingDispatcher) {
        try {
          for (connection in acceptor.incomingConnections) {
            launch {
              Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), localPortBinding.local))

                coroutineScope {
                  launch {
                    copy(socket.consumeAsEelChannel(), connection.sendChannel)
                  }

                  launch {
                    copy(connection.receiveChannel, socket.asEelChannel())
                  }
                }
              }
            }
          }
        }
        finally {
          acceptor.close()
        }
      }

      myLocalPortBindings[localPortBinding] = ResolvedPortBinding(
        localEndpoint = LocalHostPort(localPortBinding.local),
        targetEndpoint = LocalHostPort(acceptor.boundAddress.port.toInt())
      )
    }
  }

  private class EelVolume private constructor(
    private val eel: EelApi,
    override val localRoot: Path,
    override val targetRoot: String,
  ) : UploadableVolume, DownloadableVolume {
    private fun targetRootPath(): Path {
      return eel.fs.getPath(targetRoot).asNioPath()
    }

    override fun upload(relativePath: String, targetProgressIndicator: TargetProgressIndicator) {
      val from = localRoot.resolve(relativePath).normalize()
      val to = targetRootPath().resolve(relativePath).normalize()
      try {
        if (from.isSameFileAs(to)) return
      }
      catch (err: NoSuchFileException) {
        if (!Files.exists(from)) throw err
      }
      // TODO: generalize com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider.copy
      EelPathUtils.walkingTransfer(from, to, removeSource = false, copyAttributes = true)
    }

    override fun download(relativePath: String, progressIndicator: ProgressIndicator) {
      val from = targetRootPath().resolve(relativePath).normalize()
      val to = localRoot.resolve(relativePath).normalize()
      try {
        if (from.isSameFileAs(to)) return
      }
      catch (err: NoSuchFileException) {
        if (!Files.exists(from)) throw err
      }
      // TODO: generalize com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider.copy
      EelPathUtils.walkingTransfer(from, to, removeSource = false, copyAttributes = true)
    }

    override fun resolveTargetPath(relativePath: String): String {
      return targetRootPath().resolve(relativePath).asEelPath().toString()
    }

    companion object {
      private fun createFor(eel: EelApi, localPathGetter: () -> Path, targetPathGetter: () -> TargetPath): EelVolume {
        val localRootPath = localPathGetter()

        val remoteRoot = when (val targetRootPath = targetPathGetter()) {
          is TargetPath.Temporary -> {
            val localEelPath = localRootPath.asEelPath()
            if (localEelPath.descriptor == eel.descriptor) {
              localEelPath.toString()
            }
            else {
              runBlockingMaybeCancellable {
                val options = eel.fs.createTemporaryDirectory()

                targetRootPath.prefix?.let(options::prefix)
                targetRootPath.parentDirectory?.let(eel.fs::getPath)?.let(options::parentDirectory)
                options.deleteOnExit(true)

                options.getOrThrow().toString()
              }
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
        val localRootPath =
          downloadRoot.localRootPath
          ?: FileUtil.createTempDirectory("intellij-eel-target.", "").toPath()

        return createFor(eel, { localRootPath }, { downloadRoot.targetRootPath })
      }
    }
  }

  @Throws(ExecutionException::class)
  override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
    val command = commandLine.collectCommandsSynchronously()
    val builder = eel.exec.spawnProcess(command.first())

    builder.args(command.drop(1))
    builder.env(commandLine.environmentVariables)
    builder.workingDirectory(commandLine.workingDirectory?.let { EelPath.parse(it, eel.descriptor) })

    return runBlockingCancellable {
      try {
        builder.eelIt().convertToJavaProcess()
      }
      catch (e: ExecuteProcessException) {
        throw ExecutionException(e)
      }
    }
  }

  override val targetPlatform: TargetPlatform = request.targetPlatform

  override fun shutdown() {
    runBlockingMaybeCancellable {
      forwardingScope.coroutineContext.job.cancelAndJoin()
    }
  }
}

@Service
private class EelTargetScope(val scope: CoroutineScope)
