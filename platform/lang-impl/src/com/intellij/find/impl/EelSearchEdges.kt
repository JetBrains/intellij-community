// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelSearchApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/** Supplies EEL operations that tests can replace with fakes. */
@ApiStatus.Internal
class EelSearchEdges(
  val descriptorOf: (Path) -> EelDescriptor,
  val eelPathOf: (Path) -> EelPath,
  val nioPathOf: (EelPath) -> Path,
  val searchApiOf: suspend (EelDescriptor) -> EelSearchApi?,
) {
  companion object {
    fun production(): EelSearchEdges = EelSearchEdges(
      descriptorOf = { it.getEelDescriptor() },
      eelPathOf = { it.asEelPath() },
      nioPathOf = { it.asNioPath() },
      searchApiOf = { it.toEelApi().fs as? EelSearchApi },
    )
  }
}
