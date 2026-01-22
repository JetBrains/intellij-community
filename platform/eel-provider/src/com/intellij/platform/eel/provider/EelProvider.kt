// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.*
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.util.coroutines.mapNotNullConcurrent
import com.intellij.util.system.OS
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path

@ApiStatus.Experimental
interface LocalWindowsEelApi : LocalEelApi, EelWindowsApi

@ApiStatus.Experimental
interface LocalPosixEelApi : LocalEelApi, EelPosixApi

/**
 * Thrown when an EEL cannot be accessed or initialized.
 *
 * This exception indicates that the target execution environment (such as a remote machine,
 * Docker container, or WSL instance) is temporarily or permanently unavailable.
 *
 * Common scenarios include:
 * - Docker daemon connection failures
 * - Container not found or stopped
 * - Remote SSH connection issues
 * - Environment-specific setup errors
 *
 * This exception is typically thrown during:
 * - [EelProvider.tryInitialize] when initializing EEL for a project
 * - Project opening when the remote environment is unavailable
 *
 * The exception should contain a localized user-facing message explaining the specific
 * reason for unavailability, and optionally wrap the underlying cause.
 *
 * @param message Localized user-facing error message explaining why the EEL is unavailable
 * @param cause Optional underlying exception that caused the unavailability
 *
 * @see EelProvider.tryInitialize
 */
@ApiStatus.Internal
class EelUnavailableException(override val message: @Nls String, cause: Throwable? = null) : IOException(message, cause)

private val EEL_MACHINE_KEY: Key<EelMachine> = Key.create("com.intellij.platform.eel.machine")

fun Project.getEelMachine(): EelMachine {
  val descriptor = getEelDescriptor()

  if (descriptor is LocalEelDescriptor) {
    return LocalEelMachine
  }

  val cachedEelMachine = getUserData(EEL_MACHINE_KEY)

  if (cachedEelMachine != null) {
    return cachedEelMachine
  }
  else {
    val resolvedEelMachine = descriptor.getResolvedEelMachine()

    if (resolvedEelMachine != null) {
      logger.error("EelMachine is not initialized for project: $this. Using resolved EelMachine: $resolvedEelMachine")
      return resolvedEelMachine
    }

    error("Cannot find EelMachine for project: $this.")
  }
}

private fun Project.setEelMachine(machine: EelMachine) {
  putUserData(EEL_MACHINE_KEY, machine)
}

private val logger = logger<EelInitialization>()

@ApiStatus.Internal
object EelInitialization {

  @ThrowsChecked(EelUnavailableException::class)
  suspend fun runEelInitialization(path: String): EelMachine {
    val eels = EelProvider.EP_NAME.extensionList
    val machines = eels.mapNotNullConcurrent { eelProvider ->
      try {
        eelProvider.tryInitialize(path)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: EelUnavailableException) {
        throw e
      }
      catch (e: Throwable) {
        logger.error(e)
        null
      }
    }

    if (machines.isEmpty()) {
      logger.debug("No EEL machines found for path: $path")
      return LocalEelMachine
    }

    if (machines.size > 1) {
      logger.error("Several EEL machines $machines found for path: $path")
    }

    return machines.first()
  }

  @ThrowsChecked(EelUnavailableException::class)
  suspend fun runEelInitialization(project: Project) {
    if (project.isDefault) {
      return
    }

    val projectFile = project.projectFilePath
    check(projectFile != null) { "Impossible: project is not default, but it does not have project file" }

    val machine = runEelInitialization(projectFile)

    project.setEelMachine(machine)
  }
}

@ApiStatus.Experimental
fun Path.getEelDescriptor(): EelDescriptor {
  val application = ApplicationManager.getApplication()
  if (application != null) {
    for (eelProvider in EelProvider.EP_NAME.getExtensionsIfPointIsRegistered(application)) {
      eelProvider.getEelDescriptor(this)?.let { return it }
    }
  }
  return LocalEelDescriptor
}

@get:ApiStatus.Experimental
val Path.osFamily: EelOsFamily get() = getEelDescriptor().osFamily

/**
 * Retrieves [EelDescriptor] for the environment where [this] is located.
 * If the project is not the real one (i.e., it is default or not backed by a real file), then [LocalEelDescriptor] will be returned.
 */
@ApiStatus.Experimental
fun Project.getEelDescriptor(): EelDescriptor {
  @MultiRoutingFileSystemPath
  val filePath = projectFilePath
  if (filePath == null) {
    // The path to project file can be null if the project is default or used in tests.
    // While the latter is acceptable, the former can give rise to problems:
    // It is possible to "preconfigure" some settings for projects, such as default SDK or libraries.
    // This preconfiguration appears to be tricky in case of non-local projects: it would require UI changes if we want to configure WSL,
    // and in the case of Docker it is simply impossible to preconfigure a container with UI.
    // So we shall limit this preconfiguration to local projects only, which implies that the default project will be associated with the local eel descriptor.
    return LocalEelDescriptor
  }
  return Path.of(filePath).getEelDescriptor()
}

@get:ApiStatus.Experimental
val localEel: LocalEelApi by lazy {
  if (SystemInfo.isWindows) ApplicationManager.getApplication().service<LocalWindowsEelApi>()
  else ApplicationManager.getApplication().service<LocalPosixEelApi>()
}

@Deprecated("Use toEelApiBlocking() instead", ReplaceWith("toEelApiBlocking()"))
@ApiStatus.Internal
fun EelDescriptor.upgradeBlocking(): EelApi = toEelApiBlocking()

@ApiStatus.Experimental
fun EelMachine.toEelApiBlocking(descriptor: EelDescriptor): EelApi = runBlockingMaybeCancellable { toEelApi(descriptor) }

@ApiStatus.Experimental
fun EelDescriptor.toEelApiBlocking(): EelApi {
  if (this === LocalEelDescriptor) return localEel
  return runBlockingMaybeCancellable { toEelApi() }
}

@ApiStatus.Experimental
data object LocalEelMachine : EelMachine {
  override val internalName: String = "Local"

  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    check(descriptor === LocalEelDescriptor) { "Wrong descriptor: $descriptor for machine: $this" }
    return localEel
  }

  override fun ownsPath(path: Path): Boolean {
    return path.getEelDescriptor() === LocalEelDescriptor
  }
}

@ApiStatus.Experimental
data object LocalEelDescriptor : EelDescriptor {
  private val LOG = logger<LocalEelDescriptor>()

  override val name: @NonNls String = "Local: ${System.getProperty("os.name")}"

  override val osFamily: EelOsFamily by lazy {
    when {
      SystemInfo.isWindows -> EelOsFamily.Windows
      SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isFreeBSD -> EelOsFamily.Posix
      else -> {
        LOG.info("Eel is not supported on current platform")
        EelOsFamily.Posix
      }
    }
  }
}

@ApiStatus.Internal
interface EelProvider {
  companion object {
    val EP_NAME: ExtensionPointName<EelProvider> = ExtensionPointName<EelProvider>("com.intellij.eelProvider")
  }

  /**
   * Runs an initialization process for [EelApi] relevant to [path] during the process of its opening.
   *
   * This function runs **early**, so implementors need to be careful with performance.
   * This function is called for every opening [Project],
   * so the implementation is expected to exit quickly if it decides that it is not responsible for [path].
   */
  @ThrowsChecked(EelUnavailableException::class)
  suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String): EelMachine?

  /**
   * Returns the descriptor for some path or `null` if this provider doesn't support such paths.
   */
  fun getEelDescriptor(path: Path): EelDescriptor?

  fun getMountProvider(eelDescriptor: EelDescriptor): EelMountProvider? = null

  /**
   * Makes sense only on Windows, because on Posix there's the only root `/`.
   *
   * Returns additional elements to be returned by `FileSystems.getDefault().getRootDirectories()`
   */
  fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>?
}

@ApiStatus.Internal
fun EelApi.systemOs(): OS {
  return when (platform) {
    is EelPlatform.Linux -> OS.Linux
    is EelPlatform.Darwin -> OS.macOS
    is EelPlatform.Windows -> OS.Windows
    is EelPlatform.FreeBSD -> OS.FreeBSD
  }
}