// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output.impl

import com.intellij.build.events.OutputId
import com.intellij.build.output.BuildOutputText
import com.intellij.openapi.util.NlsSafe

internal data class BuildOutputTextImpl(
  override val ids: List<OutputId>,
  override val text: @NlsSafe String,
) : BuildOutputText
