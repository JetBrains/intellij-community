package com.intellij.execution.multilaunch.execution.executables

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.execution.BeforeExecuteTask
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.state.ExecutableSnapshot
import com.intellij.internal.statistic.StructuredIdeActivity
import javax.swing.Icon

abstract class Executable(
  val uniqueId: String,
  @NlsSafe val name: String,
  val icon: Icon?,
  val template: ExecutableTemplate
) {
  abstract suspend fun execute(mode: ExecutionMode, activity: StructuredIdeActivity, lifetime: Lifetime): RunContentDescriptor?
  open suspend fun cancel() {}

  open val beforeExecuteTasks: List<BeforeExecuteTask> = emptyList()
  open val supportsDebugging: Boolean = false
  open val supportsEditing: Boolean = false

  open fun performEdit() {}

  /**
   * May return null if executable is without configurable parameters.
   */
  //abstract fun provideEditor(row: Row)
  abstract fun saveAttributes(snapshot: ExecutableSnapshot)

  abstract fun loadAttributes(snapshot: ExecutableSnapshot)

  override fun equals(other: Any?): Boolean {
    return (other is Executable && other.uniqueId == uniqueId)
  }

  override fun hashCode(): Int {
    return uniqueId.hashCode()
  }
}

