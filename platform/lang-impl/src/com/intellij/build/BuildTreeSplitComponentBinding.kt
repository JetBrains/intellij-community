// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.ui.split.SplitComponentBinding
import com.intellij.util.PlatformUtils
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val BuildTreeSplitComponentBinding: SplitComponentBinding<BuildViewId> = SplitComponentBinding("BuildTree") { uid ->
  BuildViewId(uid, modelIsOnClient = false)
}

@Serializable
@ApiStatus.Internal
data class BuildViewId(
  override val uid: UID,
  private val modelIsOnClient: Boolean = PlatformUtils.isJetBrainsClient(),
) : Id {
  /**
   * This tells whether the id can be used to access the local model ([BuildTreeViewModel]) directly, using [findValue].
   */
  fun isLocal(): Boolean = modelIsOnClient == PlatformUtils.isJetBrainsClient()
}