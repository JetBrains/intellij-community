// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls


@ApiStatus.Experimental
interface ExternalSystemTasksAndArguments {
  val tasks: List<Task>
  val arguments: List<Argument>

  data class Task(@NlsSafe val name: String, @Nls val description: String?)

  data class Argument(@NlsSafe val name: String, @NlsSafe val shortName: String?, @Nls val description: String?)
}