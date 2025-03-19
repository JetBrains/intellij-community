package com.intellij.execution.multilaunch.execution

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.openapi.rd.util.lifetime
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class ExecutionSession(project: Project, val model: MultiLaunchExecutionModel) {
  private val lifetime = project.lifetime.createNested()
  private val executableLifetimes = model.executables.values.associate { it.descriptor.executable to lifetime.createNested() }

  fun getLifetime(): LifetimeDefinition {
    return lifetime
  }

  fun getLifetime(executable: Executable): LifetimeDefinition? {
    return executableLifetimes[executable]
  }

  fun stop() {
    lifetime.terminate()
  }

  fun stop(executable: Executable) {
    getLifetime(executable)?.terminate()
  }

  suspend fun awaitExecution() {
    suspendCancellableCoroutine {
      lifetime.onTermination {
        it.resume(Unit)
      }
      it.invokeOnCancellation {
        lifetime.terminate()
      }
    }
  }
}