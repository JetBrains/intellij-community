// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.div
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface EelMountProvider {
  /**
   * Checks if the given path is in a mounted volume and returns the information useful for accessing directly.
   */
  fun getMountRoot(path: EelPath): EelMountRoot?
}

@ApiStatus.Internal
interface EelMountRoot {
  val targetRoot: EelPath
  val localRoot: EelPath

  @ApiStatus.Internal
  sealed class DirectAccessOptions {
    object BasicAttributes : DirectAccessOptions()
    object PosixAttributes : DirectAccessOptions()
    object PosixAttributesAndAllAccess : DirectAccessOptions()
    object CaseSensitivity : DirectAccessOptions()
    object BasicAttributesAndWritable : DirectAccessOptions()
  }

  /**
   * Whether the operations with
   */
  fun canReadPermissionsDirectly(targetEel: EelFileSystemApi, localEel: EelFileSystemApi, options: DirectAccessOptions): Boolean
}

@ApiStatus.Internal
fun EelMountRoot.transformPath(targetPath: EelPath): EelPath {
  require(targetRoot.startsWith(targetRoot))
  return targetPath.parts.drop(targetRoot.parts.size).fold(this.localRoot) { result, part -> result / part }
}

@ApiStatus.Internal
suspend fun EelMountRoot.canReadPermissionsDirectly(options: EelMountRoot.DirectAccessOptions): Boolean {
  return canReadPermissionsDirectly(targetRoot.descriptor.toEelApi().fs, localRoot.descriptor.toEelApi().fs, options)
}