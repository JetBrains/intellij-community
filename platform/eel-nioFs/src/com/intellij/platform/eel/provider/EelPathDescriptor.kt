// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelOsFamily
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Resolves the [EelDescriptor] of [path] and checks whether this [EelMachine] owns it.
 *
 * NIO-path convenience wrapper around [EelMachine.ownsDescriptor]: the descriptor is obtained via [Path.getEelDescriptor] and the
 * ownership check is delegated to it.
 *
 * @see EelMachine.ownsDescriptor
 */
@ApiStatus.Experimental
fun EelMachine.ownsPath(path: Path): Boolean = ownsDescriptor(path.getEelDescriptor())

/**
 * Returns the [EelDescriptor] of the environment this NIO [Path] belongs to.
 *
 * IntelliJ Platform paths may point into a non-local environment. WSL paths (e.g. `\\wsl.localhost\Ubuntu\home\user\project`) and the
 * synthetic routing paths used for Docker / Dev Containers (e.g. `\\devcontainer.ij\...` on Windows or `/$devcontainer.ij/...` on Unix)
 * are recognized and mapped to the descriptor of their environment. Every other path, including ordinary local paths, resolves to
 * [LocalEelDescriptor].
 *
 * Obtaining the descriptor is a lightweight lookup: it does **not** start, deploy, or connect to an IntelliJ Agent, and it does not
 * guarantee that the environment is actually reachable. That happens later, when the descriptor is turned into a live
 * [com.intellij.platform.eel.EelApi] via [toEelApi].
 *
 * When a `Project` is available, prefer obtaining the descriptor from it: an already-open project is the safest anchor, because the
 * platform has already established access to its environment.
 *
 * Be careful with arbitrary paths. Recognizing a path as belonging to an environment does not mean that environment is currently
 * available — a routed path is only a reference to it. The connection is established lazily: any later operation that performs
 * I/O on such a path (whether by you or by code you pass the path to) starts or connects to the environment's IntelliJ Agent on
 * first use, which may be slow or may fail if the environment is unreachable. Code downstream that treats it as an ordinary local
 * path may not expect that latency or failure.
 *
 * ```kotlin
 * val descriptor = path.getEelDescriptor()
 * val eel = descriptor.toEelApi() // starts or connects to the environment
 * ```
 *
 * @see EelDescriptor
 * @see toEelApi
 */
@ApiStatus.Experimental
fun Path.getEelDescriptor(): EelDescriptor {
  return EelNioFsBackend.instance?.resolveDescriptor(this) ?: LocalEelDescriptor
}

/**
 * The [EelOsFamily] of the environment this NIO [Path] belongs to (the OS family of its [EelDescriptor]).
 *
 * This describes the *path's* environment, which is not necessarily the IDE host: a path inside a Linux container reports
 * [EelOsFamily.Posix] even when the IDE runs on Windows. Use this instead of host-side `SystemInfo` when you need the OS of the path's
 * environment.
 *
 * @see Path.getEelDescriptor
 */
@get:ApiStatus.Experimental
val Path.osFamily: EelOsFamily get() = getEelDescriptor().osFamily
