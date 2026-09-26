// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Implemented by [java.nio.file.FileSystem] classes that are authoritative sources of their [EelDescriptor].
 *
 * Used by `Path.getEelDescriptor()` to resolve the descriptor for a given path by querying the path's filesystem directly,
 * instead of iterating extension points.
 */
@ApiStatus.Internal
interface EelDescriptorOwner {
  val eelDescriptor: EelDescriptor
}
