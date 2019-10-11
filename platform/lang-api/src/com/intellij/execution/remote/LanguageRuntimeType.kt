// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.remote

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.Nls

abstract class LanguageRuntimeType<C : LanguageRuntimeConfiguration>(id: String) : BaseExtendableType<C>(id) {

  abstract fun isApplicableTo(runConfig: RunnerAndConfigurationSettings): Boolean

  /**
   * Description of type's Configurable, e.g : "Configure GO"
   */
  @get: Nls
  abstract val configurableDescription: String

  /**
   * Description of the launch of the given run configuration, e.g : "Run Java application"
   */
  @get: Nls
  abstract val launchDescription: String

  open fun createIntrospector(config: C): Introspector? = null

  companion object {
    val EXTENSION_NAME = ExtensionPointName.create<LanguageRuntimeType<*>>("com.intellij.ir.languageRuntime")
    @JvmStatic
    fun allTypes() = EXTENSION_NAME.extensionList.toArray(emptyArray<LanguageRuntimeType<*>>())
  }

  abstract class Introspectable {
    open fun getEnvironmentVariable(varName: String): String? = null
    // open fun executeScript(script: String): String? = null
    // open fun getUser(): String? = null
  }

  interface Introspector {
    fun introspect(subject: Introspectable)
  }
}
