// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.ActivityId
import com.intellij.history.ActivityPresentationProvider
import org.jetbrains.annotations.ApiStatus

internal class CommonActivityPresentationProvider: ActivityPresentationProvider {
  override val id: String get() = ID

  companion object {
    const val ID = "Common"
  }
}

@ApiStatus.Internal
object CommonActivity {
  @JvmField
  val Command = ActivityId(CommonActivityPresentationProvider.ID, "Command")
  @JvmField
  val ExternalChange = ActivityId(CommonActivityPresentationProvider.ID, "ExternalChange")
  @JvmField
  val UserLabel = ActivityId(CommonActivityPresentationProvider.ID, "UserLabel")
}