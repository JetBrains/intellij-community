// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.util.coroutines.mapNotNullConcurrent
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus

/**
 * Initializes the execution environment for a given path during project opening.
 *
 * Use this EP when environment initialization requires **side effects** beyond passive descriptor resolution,
 * such as starting a Docker container, deploying an IJent agent, or establishing an SSH connection.
 *
 * If no initializers are registered, [EelInitialization] falls back to passive resolution via
 * `Path.getEelDescriptor().resolveEelMachine()`, which works when the MRFS backend and
 * [EelMachineResolver] are sufficient (e.g., in tests or for already-running environments).
 *
 * This function runs **early**, so implementors need to be careful with performance.
 * This function is called for every opening project,
 * so the implementation is expected to exit quickly if it decides that it is not responsible for the path.
 */
@ApiStatus.Internal
interface EelEnvironmentInitializer {
  companion object {
    val EP_NAME: ExtensionPointName<EelEnvironmentInitializer> = ExtensionPointName("com.intellij.eelEnvironmentInitializer")
  }

  @ThrowsChecked(EelUnavailableException::class)
  suspend fun tryInitialize(eelDescriptor: EelDescriptor): EelMachine?
}

@ApiStatus.Internal
object EelInitialization {
  private val logger = logger<EelInitialization>()

  private suspend fun initializeCatching(initialize: @ThrowsChecked(EelUnavailableException::class) suspend () -> EelMachine?): EelMachine? {
    return try {
      initialize()
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

  @ThrowsChecked(EelUnavailableException::class)
  suspend fun runEelInitialization(eelDescriptor: EelDescriptor): EelMachine {
    val initializers = EelEnvironmentInitializer.EP_NAME.extensionList

    val machines = if (initializers.isEmpty()) {
      listOfNotNull(initializeCatching {
        eelDescriptor.resolveEelMachine()
      })
    }
    else {
      initializers.mapNotNullConcurrent { initializer ->
        initializeCatching {
          initializer.tryInitialize(eelDescriptor)
        }
      }
    }

    if (machines.isEmpty()) {
      logger.debug("No EEL machines found for descriptor: $eelDescriptor")
      return LocalEelMachine
    }

    if (machines.size > 1) {
      logger.error("Several EEL machines $machines found for descriptor: $eelDescriptor")
    }

    return machines.first()
  }
}
