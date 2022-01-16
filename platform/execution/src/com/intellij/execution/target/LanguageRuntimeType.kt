// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * Contributed type for ["com.intellij.executionTargetLanguageRuntimeType"][EXTENSION_NAME] extension point
 *
 * Contributed instances of this class define language-specific information for given [remote target][TargetEnvironmentConfiguration].
 * The language-specific run-configuration will [query][TargetEnvironmentConfiguration.runtimes] for its specific language data
 * while preparing the launch on this specific target.
 *
 * It is expected that in most cases the remote target should be [able to introspect][createIntrospector] the language-specific data
 * by executing a scripts or observing a environment variables on target machine.
 * If the data cannot be introspected, user will have manually set up it in UI
 *
 * E.g, java-specific run configurations may need to know JRE location and version on the remote machine. Both bits may be introspected by
 * running "java --version" or observing "JAVA_HOME" environment variable.
 */
abstract class LanguageRuntimeType<C : LanguageRuntimeConfiguration>(id: String) : ContributedTypeBase<C>(id) {

  abstract fun isApplicableTo(runConfig: RunnerAndConfigurationSettings): Boolean

  /**
   * Description of type's Configurable, e.g : "Configure GO"
   */
  abstract val configurableDescription: String @Nls get

  /**
   * Description of the launch of the given run configuration, e.g : "Run Java application"
   */
  abstract val launchDescription: String @Nls get

  /**
   * Defines the *target-independent* introspection protocol executed over *target-specific* [Introspectable].
   *
   * E.g, to detect java version, it makes sense to check for JAVA_HOME environment variable, and, if available, execute shell script
   * "$JAVA_HOME/bin/java --version"
   */
  open fun createIntrospector(config: C): Introspector<C>? = null

  /**
   * List of all the volume types defined in the volume runtime
   */
  open fun volumeDescriptors(): List<VolumeDescriptor> = emptyList()

  abstract fun createConfigurable(
    project: Project,
    config: C,
    targetEnvironmentType: TargetEnvironmentType<*>,
    targetSupplier: Supplier<TargetEnvironmentConfiguration>,
  ): Configurable

  abstract fun findLanguageRuntime(target: TargetEnvironmentConfiguration): C?

  companion object {
    @JvmField
    val EXTENSION_NAME = ExtensionPointName.create<LanguageRuntimeType<*>>("com.intellij.executionTargetLanguageRuntimeType")

    fun LanguageRuntimeType<*>.findVolumeDescriptor(type: VolumeType): VolumeDescriptor? =
      this.volumeDescriptors().firstOrNull { it.type == type }
  }

  /**
   * Defines the set of actions available for [Introspector] on the remote machine. Instances of this class are remote target specific.
   * Currently only static inspection of the remote environment variables and launching and inspecting the output of the shell scripts is supported.
   */
  abstract class Introspectable {
    open fun promiseEnvironmentVariable(varName: String): CompletableFuture<String?> = CompletableFuture.completedFuture(null)
    open fun promiseExecuteScript(script: String): CompletableFuture<String?> = CompletableFuture.completedFuture(null)
    open fun shutdown() = Unit
  }

  interface Introspector<C : LanguageRuntimeConfiguration> {
    fun introspect(subject: Introspectable): CompletableFuture<C>

    companion object {
      val DONE = CompletableFuture.completedFuture("")
    }
  }

  /**
   * When launching of the application on target requires the grouping of the transferred files in the target, the
   * [VolumeType] defines an unique ID to identify the specific group.
   * <p/>
   * Language runtime should allow user to configure the remote locations for volume roots along with the target-specific properties for
   * the transfer.
   */
  data class VolumeType(val id: String)

  /**
   * Volume descriptor is identified by its [type] and defines the UI properties to explain user the semantic of the volume.
   */
  data class VolumeDescriptor @JvmOverloads constructor(
    val type: VolumeType,
    @Nls val wizardLabel: String,
    @Nls val description: String,
    @NlsContexts.DialogTitle val browsingTitle: String,
    @NonNls val defaultPath: String,
    @NonNls val directoryPrefix: String? = null
  ) {

    @JvmOverloads
    constructor(typeId: String,
                @Nls wizardLabel: String,
                @Nls description: String,
                @NlsContexts.DialogTitle browsingTitle: String,
                @NonNls defaultPath: String,
                @NonNls directoryPrefix: String? = null) :
      this(VolumeType(typeId), wizardLabel, description, browsingTitle, defaultPath, directoryPrefix)
  }
}
