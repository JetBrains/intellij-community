// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.executors

import com.intellij.execution.Executor
import com.intellij.execution.Executor.shortenNameIfNeeded
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.TextWithMnemonic
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.Icon
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * See [com.intellij.execution.impl.DefaultExecutorGroup]
 */
abstract class ExecutorGroup<Settings : RunExecutorSettings> : Executor() {
  private val customSettingsLock = ReentrantReadWriteLock()
  private val executorId2customSettings = mutableMapOf<String, Settings>() //guarded by lock
  private val customSettings2Executor = mutableMapOf<Settings, ProxyExecutor>() //guarded by lock
  private val nextCustomExecutorId = AtomicLong()

  abstract fun getRunToolbarActionText(param: String): @NlsActions.ActionText String
  abstract fun getRunToolbarChooserText(): @NlsActions.ActionText String

  protected fun registerSettings(settings: Settings) {
    customSettingsLock.write {
      val newId = "$id#${nextCustomExecutorId.incrementAndGet()}"
      executorId2customSettings[newId] = settings
      customSettings2Executor[settings] = ProxyExecutor(settings, newId)
    }
  }

  protected fun unregisterSettings(settings: Settings) {
    customSettingsLock.write {
      val executor = customSettings2Executor.remove(settings)
      if (executor != null) {
        executorId2customSettings.remove(executor.id)
      }
    }
  }

  protected fun allRegisteredSettings(): List<Pair<String, Settings>> {
    return customSettingsLock.read {
      executorId2customSettings.map { (id, setting) -> id to setting }
    }
  }

  /**
   * When any of [ExecutorGroup.childExecutors] started, [ProxyExecutor] associated with the selected [Settings] instance is used.
   * That [ProxyExecutor] is passed through the whole execution system just like any other executor (e.g: [com.intellij.execution.executors.DefaultDebugExecutor].
   *
   * You can access the selected [Settings] by calling [ExecutorGroup.getRegisteredSettings] from the appropriate method of your own
   * [com.intellij.execution.configuration.RunConfigurationExtensionBase] implementation.
   *
   * see [com.intellij.execution.RunConfigurationExtension.updateJavaParameters(T, JavaParameters, RunnerSettings, Executor)]
   */
  fun getRegisteredSettings(proxyExecutorId: String): Settings? {
    return customSettingsLock.read {
      executorId2customSettings[proxyExecutorId]
    }
  }

  open fun childExecutors(): List<Executor> {
    return customSettingsLock.read {
      customSettings2Executor.values.toList()
    }
  }

  companion object {
    @JvmStatic
    fun getGroupIfProxy(executor: Executor): ExecutorGroup<*>? = (executor as? ExecutorGroup<*>.ProxyExecutor)?.group()
  }

  private inner class ProxyExecutor(private val settings: RunExecutorSettings, private val executorId: String) : Executor() {
    override fun getToolWindowId(): String = this@ExecutorGroup.toolWindowId

    override fun getToolWindowIcon(): Icon = this@ExecutorGroup.toolWindowIcon

    override fun getIcon(): Icon = settings.icon

    override fun getDisabledIcon(): Icon? = null

    override fun getDescription(): String = this@ExecutorGroup.description

    override fun getActionName(): String = settings.actionName

    override fun getId(): String = executorId

    override fun getStartActionText(): String = settings.startActionText

    override fun getStartActionText(configurationName: String): String = settings.getStartActionText(configurationName)

    override fun getContextActionId(): String {
      throw UnsupportedOperationException("ProxyExecutor can't be used to create context action")
    }

    override fun getHelpId(): String? = null

    override fun isApplicable(project: Project): Boolean = settings.isApplicable(project)

    override fun isSupportedOnTarget(): Boolean = group().isSupportedOnTarget

    fun group() = this@ExecutorGroup
  }
}

interface RunExecutorSettings {
  val icon: Icon
  val actionName: String
  val startActionText: String
  /**
   * @see com.intellij.execution.Executor.getStartActionText
   */
  fun getStartActionText(@NlsSafe configurationName: String): String {
    val configName = if (StringUtil.isEmpty(configurationName)) "" else " '" + shortenNameIfNeeded(configurationName) + "'"
    return TextWithMnemonic.parse(startActionText).append(configName).toString()
  }

  fun isApplicable(project: Project): Boolean

  fun canRun(profile: RunProfile): Boolean
}