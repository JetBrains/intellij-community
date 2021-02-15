// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.execution.*
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase

class ExecutionEnvironmentBuilder(private val project: Project, private var executor: Executor) {
  private var runProfile: RunProfile? = null
  private var target = DefaultExecutionTarget.INSTANCE
  private var runnerSettings: RunnerSettings? = null
  private var configurationSettings: ConfigurationPerRunnerSettings? = null
  private var contentToReuse: RunContentDescriptor? = null
  private var runnerAndConfigurationSettings: RunnerAndConfigurationSettings? = null
  private var runner: ProgramRunner<*>? = null
  private var executionId: Long? = null
  private var dataContext: DataContext? = null
  private val userData = UserDataHolderBase()
  private var modulePath: String? = null

  /**
   * Creates an execution environment builder initialized with a copy of the specified environment.
   *
   * @param env the environment to copy from.
   */
  constructor(env: ExecutionEnvironment) : this(env.project, env.executor) {
    target = env.executionTarget
    runnerAndConfigurationSettings = env.runnerAndConfigurationSettings
    runProfile = env.runProfile
    runnerSettings = env.runnerSettings
    configurationSettings = env.configurationSettings
    runner = env.runner
    contentToReuse = env.contentToReuse
    env.copyUserDataTo(userData)
  }

  companion object {
    @JvmStatic
    @Throws(ExecutionException::class)
    fun create(project: Project, executor: Executor, runProfile: RunProfile): ExecutionEnvironmentBuilder {
      return createOrNull(project, executor, runProfile)
             ?: throw ExecutionException(ExecutionBundle.message("dialog.message.cannot.find.runner", runProfile.name))
    }

    @JvmStatic
    fun createOrNull(project: Project, executor: Executor, runProfile: RunProfile): ExecutionEnvironmentBuilder? {
      val runner = ProgramRunner.getRunner(executor.id, runProfile) ?: return null
      return ExecutionEnvironmentBuilder(project, executor).runner(runner).runProfile(runProfile)
    }

    @JvmStatic
    fun createOrNull(executor: Executor, settings: RunnerAndConfigurationSettings): ExecutionEnvironmentBuilder? {
      val builder = createOrNull(executor, settings.configuration)
      return builder?.runnerAndSettings(builder.runner!!, settings)
    }

    @JvmStatic
    fun createOrNull(executor: Executor, configuration: RunConfiguration): ExecutionEnvironmentBuilder? {
      val builder = createOrNull(configuration.project, executor, configuration)
      builder?.runProfile(configuration)
      return builder
    }

    @JvmStatic
    @Throws(ExecutionException::class)
    fun create(executor: Executor, settings: RunnerAndConfigurationSettings): ExecutionEnvironmentBuilder {
      val configuration = settings.configuration
      val builder = create(configuration.project, executor, configuration)
      return builder.runnerAndSettings(builder.runner!!, settings)
    }

    @JvmStatic
    fun create(executor: Executor, configuration: RunConfiguration): ExecutionEnvironmentBuilder {
      return ExecutionEnvironmentBuilder(configuration.project, executor).runProfile(configuration)
    }
  }

  fun target(target: ExecutionTarget?): ExecutionEnvironmentBuilder {
    if (target != null) {
      this.target = target
    }
    return this
  }

  fun activeTarget(): ExecutionEnvironmentBuilder {
    target = ExecutionTargetManager.getActiveTarget(project)
    return this
  }

  fun runnerAndSettings(runner: ProgramRunner<*>, settings: RunnerAndConfigurationSettings): ExecutionEnvironmentBuilder {
    runnerAndConfigurationSettings = settings
    runProfile = settings.configuration
    runnerSettings = settings.getRunnerSettings(runner)
    configurationSettings = settings.getConfigurationSettings(runner)
    this.runner = runner
    return this
  }

  fun runnerSettings(runnerSettings: RunnerSettings?): ExecutionEnvironmentBuilder {
    this.runnerSettings = runnerSettings
    return this
  }

  fun contentToReuse(contentToReuse: RunContentDescriptor?): ExecutionEnvironmentBuilder {
    this.contentToReuse = contentToReuse
    return this
  }

  fun runProfile(runProfile: RunProfile): ExecutionEnvironmentBuilder {
    this.runProfile = runProfile
    return this
  }

  fun runner(runner: ProgramRunner<*>): ExecutionEnvironmentBuilder {
    this.runner = runner
    return this
  }

  fun dataContext(dataContext: DataContext?): ExecutionEnvironmentBuilder {
    this.dataContext = dataContext
    return this
  }

  fun executor(executor: Executor): ExecutionEnvironmentBuilder {
    this.executor = executor
    return this
  }

  fun executionId(executionId: Long): ExecutionEnvironmentBuilder {
    this.executionId = executionId
    return this
  }

  fun modulePath(modulePath: String): ExecutionEnvironmentBuilder {
    this.modulePath = modulePath
    return this
  }

  @JvmOverloads
  fun build(callback: ProgramRunner.Callback? = null): ExecutionEnvironment {
    var environment: ExecutionEnvironment? = null
    val environmentProvider = project.getService(ExecutionEnvironmentProvider::class.java)
    if (environmentProvider != null) {
      environment = environmentProvider.createExecutionEnvironment(
        project, runProfile!!, executor, target, runnerSettings, configurationSettings, runnerAndConfigurationSettings)
    }
    if (environment == null && runner == null) {
      runner = ProgramRunner.getRunner(executor.id, runProfile!!)
    }
    if (environment == null && runner == null) {
      throw IllegalStateException("Runner must be specified")
    }
    if (environment == null) {
      environment = ExecutionEnvironment(runProfile!!, executor, target, project, runnerSettings,
                                         configurationSettings, contentToReuse,
                                         runnerAndConfigurationSettings, runner!!, callback)
    }
    if (executionId != null) {
      environment.executionId = executionId!!
    }
    if (dataContext != null) {
      environment.setDataContext(dataContext!!)
    }
    if (modulePath != null) {
      environment.setModulePath(modulePath!!)
    }
    userData.copyUserDataTo(environment)
    return environment
  }

  @Throws(ExecutionException::class)
  fun buildAndExecute() {
    val environment = build()
    runner!!.execute(environment)
  }
}