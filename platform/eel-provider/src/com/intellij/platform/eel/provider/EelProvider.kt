// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelProviderProjectUtilKt")
package com.intellij.platform.eel.provider

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val logger = logger<EelInitialization>()

private val EEL_MACHINE_KEY: Key<EelMachine> = Key.create("com.intellij.platform.eel.machine")
private val EEL_DESCRIPTOR_KEY: Key<EelDescriptor> = Key.create("com.intellij.platform.eel.descriptor")

/**
 * Returns the [EelMachine] of the environment this project lives in.
 *
 * Non-suspending accessor: it returns [LocalEelMachine] for local projects, otherwise the machine the platform associated with the
 * project when it was opened. Unlike [resolveEelMachine] it does not perform resolution; it expects the project's environment to be
 * already initialized and throws if no machine can be determined.
 */
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

/**
 * Associates [machine] with this project. Called by the platform during project initialization; not for general use.
 */
@ApiStatus.Internal
fun Project.setEelMachine(machine: EelMachine) {
  putUserData(EEL_MACHINE_KEY, machine)
}

/**
 * Retrieves [EelDescriptor] for the environment where [this] is located.
 * If the project is not the real one (i.e., it is default or not backed by a real file), then [LocalEelDescriptor] will be returned,
 * unless an explicit descriptor has been set via [setEelDescriptor] (e.g., for RD thin client with a fake project).
 */
@ApiStatus.Experimental
fun Project.getEelDescriptor(): EelDescriptor {
  getUserData(EEL_DESCRIPTOR_KEY)?.let { return it }

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

/**
 * Explicitly associates an [EelDescriptor] with this project.
 * This is useful for projects that are not backed by a real file path (e.g., the default project in RD thin client),
 * where the descriptor cannot be inferred from the project file path.
 */
@ApiStatus.Internal
fun Project.setEelDescriptor(descriptor: EelDescriptor) {
  putUserData(EEL_DESCRIPTOR_KEY, descriptor)
}

/**
 * Blocking equivalent of [EelMachine.toEelApi]: connects to the environment on the current thread.
 *
 * It blocks the calling thread until the environment is reached, and starting or connecting may be slow, so do not call it on the EDT;
 * prefer the suspending [EelMachine.toEelApi] whenever you are in a coroutine. Like its suspending counterpart, it can fail with
 * [com.intellij.platform.eel.EelUnavailableException].
 */
@ApiStatus.Experimental
fun EelMachine.toEelApiBlocking(descriptor: EelDescriptor): EelApi = runBlockingMaybeCancellable { toEelApi(descriptor) }

/**
 * Blocking equivalent of [toEelApi]: resolves the machine and connects to the environment on the current thread.
 *
 * It blocks the calling thread until the environment is reached, and starting or connecting may be slow, so do not call it on the EDT;
 * prefer the suspending [toEelApi] whenever you are in a coroutine. Like its suspending counterpart, it can fail with
 * [com.intellij.platform.eel.EelUnavailableException].
 */
@ApiStatus.Experimental
fun EelDescriptor.toEelApiBlocking(): EelApi {
  if (this === LocalEelDescriptor) return localEel
  return runBlockingMaybeCancellable { toEelApi() }
}