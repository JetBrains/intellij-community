// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui

interface ExternalSystemTasksAndArguments {
  val tasks: List<Task>
  val arguments: List<Argument>

  data class Task(val name: String, val description: String?)

  data class Argument(val name: String, val shortName: String?, val description: String?)
}