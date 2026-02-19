// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.commit

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.vcs.commit.CommitMode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DvcsCommitModeProvider {
  fun getCommitMode(): CommitMode?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DvcsCommitModeProvider> = ExtensionPointName("com.intellij.commitModeProvider")

    @JvmStatic
    fun compute(): CommitMode? = EP_NAME.computeSafeIfAny { it.getCommitMode() }
  }
}
