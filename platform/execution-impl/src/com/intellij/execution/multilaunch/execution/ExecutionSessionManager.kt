package com.intellij.execution.multilaunch.execution

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.PROJECT)
internal class ExecutionSessionManager {
  companion object {
    fun getInstance(project: Project) = project.service<ExecutionSessionManager>()
  }

  private var activeSessions = mutableMapOf<MultiLaunchConfiguration, ExecutionSession>()
  private val rwLock = ReentrantReadWriteLock()

  fun setActiveSession(configuration: MultiLaunchConfiguration, session: ExecutionSession?) {
    rwLock.write {
      when (session) {
        null -> activeSessions.remove(configuration)
        else -> activeSessions.put(configuration, session)
      }
    }
  }

  fun getActiveSession(configuration: MultiLaunchConfiguration): ExecutionSession? {
    return rwLock.read {
      activeSessions[configuration]
    }
  }
}