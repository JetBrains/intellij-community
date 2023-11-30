package com.intellij.execution.multilaunch.execution.conditions.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindIntText
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.execution.ExecutionDescriptor
import com.intellij.execution.multilaunch.execution.messaging.DefaultExecutionNotifier
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.conditions.ConditionTemplate
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.messaging.ExecutionNotifier
import com.intellij.execution.multilaunch.state.ConditionSnapshot
import com.intellij.internal.statistic.StructuredIdeActivity
import kotlinx.coroutines.delay
import java.net.Socket

class AfterPortOpenedConditionTemplate : ConditionTemplate {
  override val type = "waitPortOpened"

  override fun createCondition() = AfterPortOpenedCondition()

  inner class AfterPortOpenedCondition : Condition(this) {
    private val PORT_ATTRIBUTE = "port"
    private val DEFAULT_PORT = 80

    private var port: Int = DEFAULT_PORT
    override val text get() = ExecutionBundle.message("run.configurations.multilaunch.condition.after.port.opened", port)

    override fun provideEditor(row: Row) = row
      .intTextField(IntRange(0, 65536))
      .bindIntText({ port }, { port = it })

    override fun validate(configuration: MultiLaunchConfiguration, row: ExecutableRow) {}

    override fun createExecutionListener(descriptor: ExecutionDescriptor,
                                         mode: ExecutionMode,
                                         activity: StructuredIdeActivity,
                                         lifetime: Lifetime): ExecutionNotifier =
      Listener(descriptor.executable, mode, activity, lifetime)

    override fun saveAttributes(snapshot: ConditionSnapshot) {
      snapshot.attributes[PORT_ATTRIBUTE] = port.toString()
    }

    override fun loadAttributes(snapshot: ConditionSnapshot) {
      port = snapshot.attributes[PORT_ATTRIBUTE]?.toInt() ?: DEFAULT_PORT
    }

    inner class Listener(
      private val executable: Executable,
      private val mode: ExecutionMode,
      private val activity: StructuredIdeActivity,
      private val lifetime: Lifetime
    ) : DefaultExecutionNotifier() {
      override fun start(configuration: MultiLaunchConfiguration, executables: List<Executable>) {
        lifetime.launchBackground {
          if (isPortOpen("localhost", port, 1_000, 60)) {
            executable.execute(mode, activity, lifetime)
          }
        }
      }

      private suspend fun isPortOpen(host: String, port: Int, timeoutMs: Long, retries: Int): Boolean {
        var success = false
        var lastException: Exception? = null

        repeat(retries) {
          delay(timeoutMs)
          try {
            Socket().use {
              it.connect(java.net.InetSocketAddress(host, port), 1000)
              success = true
            }
          } catch (e: Exception) {
            lastException = e
          }
          if (success) return true
        }
        if (!success) lastException?.printStackTrace()
        return success
      }
    }
  }
}