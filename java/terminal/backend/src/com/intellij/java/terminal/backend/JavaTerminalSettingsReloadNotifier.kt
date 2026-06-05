// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.backend

import com.intellij.java.terminal.shared.JavaTerminalBundle
import com.intellij.java.terminal.shared.JavaTerminalSettings
import com.intellij.java.terminal.shared.JavaTerminalSettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.rpc.topics.broadcast
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.TerminalEnvironmentChanged

/** Key being set only when a user clicks on the `Configure JDK...` inlay from [NoJavaExecutableFilter] */
internal val SHOULD_DISPLAY_NOTIFICATION: Key<Boolean> = KeyWithDefaultValue.create("JAVA_TERMINAL_SHOULD_DISPLAY_NOTIFICATION", false)

@Service(Service.Level.PROJECT)
internal class JavaTerminalSettingsReloadNotifier(
  private val project: Project,
  private val scope: CoroutineScope,
) : Disposable {
  companion object {
    fun getInstance(project: Project): JavaTerminalSettingsReloadNotifier = project.service<JavaTerminalSettingsReloadNotifier>()
  }

  private val listener = JavaTerminalSettingsListener {
    notifyTopic()
  }

  fun subscribe() {
    JavaTerminalSettings.instance.addListener(listener)
    scope.launch {
      project.workspaceModel.eventLog.collect { event ->
        if (project.getUserData(SHOULD_DISPLAY_NOTIFICATION) != true) return@collect
        if (!JavaTerminalSettings.instance.overrideJavaHome) return@collect

        val changes = event.getChanges(ProjectSettingsEntity::class.java)
        if (changes.isEmpty()) return@collect

        val oldEntity = changes.first().oldEntity
        val newEntity = changes.first().newEntity
        if (oldEntity?.projectSdk?.presentableName == newEntity?.projectSdk?.presentableName) return@collect

        notifyTopic()
      }
    }
  }

  private fun notifyTopic() {
    TerminalEnvironmentChanged.TOPIC.broadcast(project,
                                               TerminalEnvironmentChanged.EnvironmentChange(JavaTerminalBundle.message("environment.changed.type")))
    project.putUserData(SHOULD_DISPLAY_NOTIFICATION, false)
  }

  override fun dispose() {
    JavaTerminalSettings.instance.removeListener(listener)
  }
}

internal class JavaTerminalSettingsReloadActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    JavaTerminalSettingsReloadNotifier.getInstance(project).subscribe()
  }
}
