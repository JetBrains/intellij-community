// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class NewUiOnboardingStepData(
  val builder: GotItComponentBuilder,
  val relativePoint: RelativePoint,
  // null means that the balloon should be shown in the center of the component referenced in relativePoint
  val position: Balloon.Position?
)