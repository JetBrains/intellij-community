// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation

class DocumentationRequest(
  val targetPointer: Pointer<out DocumentationTarget>,
  val presentation: TargetPresentation,
)
