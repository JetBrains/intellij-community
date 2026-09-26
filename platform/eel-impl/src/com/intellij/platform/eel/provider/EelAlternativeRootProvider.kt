// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import org.jetbrains.annotations.ApiStatus

/**
 * Every local path prefix under which the files of this environment are reachable: [EelPathBoundDescriptor.rootPath]
 * plus the roots from [EelAlternativeRootProvider]. The consumers compare prefixes as strings (the JPS build strips them
 * from paths), so notations of one root that compare equal as `Path` objects, such as `\\wsl$\Ubuntu\` and
 * `\\wsl.localhost\Ubuntu\`, are all kept.
 */
@ApiStatus.Internal
fun EelDescriptor.routingPrefixes(): List<@MultiRoutingFileSystemPath String> {
  val alternativeRoots = EelAlternativeRootProvider.EP_NAME.extensionList.flatMap { it.getAlternativeRoots(this) ?: emptyList() }
  return (alternativeRoots + listOfNotNull((this as? EelPathBoundDescriptor)?.rootPath?.toString())).distinct()
}

/**
 * Provides alternative local filesystem roots for environments that are reachable via multiple paths.
 *
 * Most EEL environments have a single root ([EelPathBoundDescriptor.rootPath]).
 * Some environments are accessible through multiple local paths — for example,
 * a WSL distribution can be accessed via both `\\wsl.localhost\Ubuntu` and `\\wsl$\Ubuntu`.
 *
 * [EelDescriptor.routingPrefixes][com.intellij.platform.eel.provider.routingPrefixes] combines
 * [EelPathBoundDescriptor.rootPath] with roots from this EP. The result is consumed by the
 * JPS build process (`-Dide.jps.remote.path.prefixes`) to strip environment-specific
 * path prefixes during remote compilation.
 *
 * Implement this EP only if your environment has alternative paths beyond [EelPathBoundDescriptor.rootPath].
 */
@ApiStatus.Internal
interface EelAlternativeRootProvider {
  companion object {
    val EP_NAME: ExtensionPointName<EelAlternativeRootProvider> = ExtensionPointName("com.intellij.eelAlternativeRootProvider")
  }

  fun getAlternativeRoots(descriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>?
}
