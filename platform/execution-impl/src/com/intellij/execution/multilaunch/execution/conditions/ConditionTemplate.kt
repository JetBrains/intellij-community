package com.intellij.execution.multilaunch.execution.conditions

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ConditionTemplate {
  companion object {
    val EP_NAME = ExtensionPointName<ConditionTemplate>("com.intellij.multilaunch.condition.template")
  }

  val type: String

  fun createCondition(): Condition
}