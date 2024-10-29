package com.intellij.execution

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Returns a list of [RunContentDescriptor] objects started in the current [Project] suitable to stop them.
 *
 * Each descriptor is paired with a [RunnerAndConfigurationSettings] (if it was associated with such an object) for presentation purposes.
 * Stopping a descriptor is supposed to stop a session of the corresponding [RunnerAndConfigurationSettings]. Not every descriptor may have
 * an associated run configuration.
 */
internal fun getStoppableDescriptors(project: Project): List<Pair<RunContentDescriptor, RunnerAndConfigurationSettings?>> {
  val manager = ExecutionManagerImpl.getInstanceIfCreated(project) ?: return emptyList()
  val allDescriptors = ExecutionManagerImpl.getAllDescriptors(project).asReversed()
  val map = mutableMapOf<ExecutionEnvironment, MutableList<RunContentDescriptor>>()
  val list = mutableListOf<RunContentDescriptor>()
  for (descriptor in allDescriptors) {
    val processHandler = descriptor.processHandler ?: continue
    if (processHandler.isProcessTerminated) {
      continue
    }
    val envs = manager.getExecutionEnvironments(descriptor)
    when (envs.size) {
      0 -> {
        val extensions = DisplayDescriptorChooser.EP_NAME.extensionList
        var chosenEnv: ExecutionEnvironment? = null
        for (extension in extensions) {
          chosenEnv = extension.getExecutionEnvironment(descriptor)
          if (chosenEnv != null) {
            map.getOrPut(chosenEnv) { mutableListOf() }.add(descriptor)
          }
        }
        if (chosenEnv == null) {
          list.add(descriptor)
        }
      }
      else -> {
        if (envs.size > 1) {
          logger<ExecutionManagerImpl>().warn("Multiple execution environments for given run content descriptor: ${descriptor.displayName}")
        }
        map.getOrPut(envs.first()) { mutableListOf() }.add(descriptor)
      }
    }
  }

  return buildList {
    for ((env, descriptors) in map) {
      val chosenDescriptor = chooseDisplayDescriptor(env, descriptors)
      if (chosenDescriptor == null) {
        addAll(descriptors.map { it to null })
        continue
      }
      add(chosenDescriptor to env.runnerAndConfigurationSettings)
    }

    addAll(list.map { it to null })
  }
}

private fun chooseDisplayDescriptor(env: ExecutionEnvironment, descriptors: List<RunContentDescriptor>): RunContentDescriptor? {
  val extensions = DisplayDescriptorChooser.EP_NAME.extensionList
  for (extension in extensions) {
    val descriptor = extension.chooseDescriptor(descriptors, env)
    if (descriptor != null) {
      return descriptor
    }
  }
  return null
}

/**
 * Allows customization of [getStoppableDescriptors] behavior.
 *
 * Normally, one [RunContentDescriptor] corresponds to one active session of a [RunnerAndConfigurationSettings].
 *
 * In certain cases, a [RunnerAndConfigurationSettings] may start several instances of [RunContentDescriptor], that logically still belong
 * to a single session, and get terminated simultaneously.
 *
 * In those cases, a [DisplayDescriptorChooser] will get asked to provide the "representable" descriptor (the one that will be chosen to be
 * shown on the UI), of all the running descriptors belonging to the session.
 */
interface DisplayDescriptorChooser {
  companion object {
    val EP_NAME = ExtensionPointName.create<DisplayDescriptorChooser>("com.intellij.execution.displayDescriptorChooser")
  }

  /**
   * Allows to associate a [RunContentDesctriptor] with an [ExecutionEnvironment], in cases, when
   * [ExecutionManagerImpl.getExecutionEnvironments] returns an empty set of [ExecutionEnvironment] for a particular
   * [RunContentDesctriptor]. That happens in cases there are several [RunContentDesctriptor] for one [ExecutionEnvironment], which may be
   * hard to indicate via means other than this extension.
   *
   * Return `null` if current [DisplayDescriptorChooser] doesn't support a passed [RunContentDescriptor].
   */
  fun getExecutionEnvironment(descriptor: RunContentDescriptor): ExecutionEnvironment?

  /**
   * Gets passed a list of all running [RunContentDescriptor] objects associated with an [ExecutionEnvironment], and allows to choose the
   * main one among them.
   */
  fun chooseDescriptor(relatedDescriptors: List<RunContentDescriptor>, environment: ExecutionEnvironment): RunContentDescriptor?
}