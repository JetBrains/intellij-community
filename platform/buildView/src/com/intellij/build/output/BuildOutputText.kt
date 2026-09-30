// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.OutputId
import com.intellij.build.output.impl.BuildOutputTextImpl
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable

@Internal
@NonExtendable
interface BuildOutputText {

  val ids: List<OutputId>

  val text: @NlsSafe String

  companion object {

    fun createOutputText(text: String): BuildOutputText =
      BuildOutputTextImpl(emptyList(), text)

    fun createOutputText(id: OutputId, text: String): BuildOutputText =
      BuildOutputTextImpl(listOf(id), text)

    fun createOutputText(ids: List<OutputId>, text: String): BuildOutputText =
      BuildOutputTextImpl(ids, text)
  }
}