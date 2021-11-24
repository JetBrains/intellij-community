// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state.createdProject

import com.intellij.util.xmlb.annotations.OptionTag

data class NewProjectInfoState(
  @OptionTag(converter = NewProjectInfoConverter::class)
  val createdProjectInfo: MutableList<NewProjectInfoEntry> = mutableListOf()
)
