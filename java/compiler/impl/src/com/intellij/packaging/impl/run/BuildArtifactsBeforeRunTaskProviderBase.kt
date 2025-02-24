// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.run

import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.ConfigurationSettingsEditorWrapper
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.DataManager
import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.packaging.artifacts.*
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.useReactiveWorkspaceModelApi
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.JComponent

private val query = entities<ArtifactEntity>()

@Service(Service.Level.PROJECT)
private class ArtifactListenerService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  fun <T : BuildArtifactsBeforeRunTaskBase<*>> start(providerId: Key<T>) {
    cs.launch {
      (project.workspaceModel as WorkspaceModelInternal).flowOfDiff(query).collect {
        if (it.removed.any()) {
          edtWriteAction {
            it.removed.forEach { artifactEntity ->
              removeByName(project, providerId, artifactEntity.name)
            }
          }
        }
      }
    }
  }
}

@Suppress("FINITE_BOUNDS_VIOLATION_IN_JAVA")
abstract class BuildArtifactsBeforeRunTaskProviderBase<T : BuildArtifactsBeforeRunTaskBase<*>>(
  private val taskClass: Class<T>,
  private val project: Project
) : BeforeRunTaskProvider<T>() {
  init {
    if (useReactiveWorkspaceModelApi()) {
      project.service<ArtifactListenerService>().start(id)
    } else {
      project.messageBus.connect().subscribe(ArtifactManager.TOPIC, MyArtifactListener(project, id))
    }
  }

  override fun isConfigurable() = true

  @Suppress("OVERRIDE_DEPRECATION")
  override fun configureTask(runConfiguration: RunConfiguration, task: T): Boolean {
    val artifacts = ArtifactManager.getInstance(project).artifacts
    val pointers = HashSet<ArtifactPointer>()

    if (artifacts.isNotEmpty()) {
      val artifactPointerManager = ArtifactPointerManager.getInstance(project)
      for (artifact in artifacts) {
        pointers.add(artifactPointerManager.createPointer(artifact!!))
      }
    }
    pointers.addAll(task.artifactPointers)
    val chooser = ArtifactChooser(pointers.toList())
    chooser.markElements(task.artifactPointers)
    chooser.preferredSize = JBUI.size(400, 300)
    val builder = DialogBuilder(project)
    builder.setTitle(JavaCompilerBundle.message("build.artifacts.before.run.selector.title"))
    builder.setDimensionServiceKey("#BuildArtifactsBeforeRunChooser")
    builder.addOkAction()
    builder.addCancelAction()
    builder.setCenterPanel(chooser)
    builder.setPreferredFocusComponent(chooser)
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      task.artifactPointers = chooser.markedElements
      return true
    }
    return false
  }

  override fun createTask(runConfiguration: RunConfiguration): T? {
    return if (project.isDefault) null else doCreateTask(project)
  }

  override fun canExecuteTask(configuration: RunConfiguration, task: T): Boolean {
    return task.artifactPointers.any { it.artifact != null }
  }

  override fun executeTask(context: DataContext, configuration: RunConfiguration, env: ExecutionEnvironment, task: T): Boolean {
    val artifacts = ArrayList<Artifact>()
    ApplicationManager.getApplication().runReadAction {
      for (pointer in task.artifactPointers) {
        pointer.artifact?.let(artifacts::add)
      }
    }
    if (project.isDisposed) {
      return false
    }

    val artifactsBuildProjectTask = createProjectTask(project, artifacts)
    val sessionId = ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.get(env)
    val projectTaskContext = ProjectTaskContext(sessionId)
    env.copyUserDataTo(projectTaskContext)
    ProjectTaskManagerImpl.putBuildOriginator(project, this.javaClass)
    val resultPromise = ProjectTaskManager.getInstance(project).run(projectTaskContext, artifactsBuildProjectTask)
    val taskResult = ProjectTaskManagerImpl.waitForPromise(resultPromise)
    return taskResult != null && !taskResult.isAborted && !taskResult.hasErrors()
  }

  protected fun setBuildArtifactBeforeRunOption(runConfigurationEditorComponent: JComponent, artifact: Artifact, enable: Boolean) {
    val dataContext = DataManager.getInstance().getDataContext(runConfigurationEditorComponent)
    val editor = ConfigurationSettingsEditorWrapper.CONFIGURATION_EDITOR_KEY.getData(dataContext) ?: return
    val tasks: List<BuildArtifactsBeforeRunTaskBase<*>> = editor.stepsBeforeLaunch
      .mapNotNull { if (taskClass.isInstance(it)) it as BuildArtifactsBeforeRunTaskBase else null }
    if (enable && tasks.isEmpty()) {
      val task = doCreateTask(project)
      task.addArtifact(artifact)
      task.isEnabled = true
      editor.addBeforeLaunchStep(task)
    }
    else {
      for (task in tasks) {
        if (enable) {
          task.addArtifact(artifact)
          task.setEnabled(true)
        }
        else {
          task.removeArtifact(artifact)
          if (task.artifactPointers.isEmpty()) {
            task.isEnabled = false
          }
        }
      }
    }
  }

  protected abstract fun doCreateTask(project: Project?): T

  protected abstract fun createProjectTask(project: Project, artifacts: List<Artifact>): ProjectTask
}

@Suppress("FINITE_BOUNDS_VIOLATION_IN_JAVA")
private class MyArtifactListener<T : BuildArtifactsBeforeRunTaskBase<*>>(
  private val project: Project,
  private val providerId: Key<T>
) : ArtifactListener {
  override fun artifactRemoved(artifact: Artifact) {
    val artifactName = artifact.name
    removeByName(project, providerId, artifactName)
  }
}

private fun <T : BuildArtifactsBeforeRunTaskBase<*>> removeByName(project: Project, providerId: Key<T>, artifactName: @NlsSafe String) {
  // It looks like there are no checks for modification of the task that write access is required. However,
  //   I think it's needed because it's a modification
  // At least, ArtifactManager.findArtifact requires read action
  ThreadingAssertions.assertWriteAccess()

  val runManager = RunManagerEx.getInstanceEx(project)
  for (configuration in runManager.allConfigurationsList) {
    val tasks = runManager.getBeforeRunTasks(configuration, providerId)
    for (task in tasks) {
      for (pointer in task.artifactPointers.toList()) {
        if (pointer.artifactName == artifactName && ArtifactManager.getInstance(project).findArtifact(artifactName) == null) {
          task.removeArtifact(pointer)
        }
      }
    }
  }
}
