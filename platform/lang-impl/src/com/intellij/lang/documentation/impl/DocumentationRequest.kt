// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.impl

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DocumentationRequest(
  val targetPointer: Pointer<out DocumentationTarget>,
  val presentation: TargetPresentation,
)
