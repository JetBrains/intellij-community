// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util.environment

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.replaceService
import com.intellij.util.application

class TestEnvironment : Environment {
  private val properties = LinkedHashMap<String, String?>()
  private val variables = LinkedHashMap<String, String?>()
  private val previousEnvironment = Environment.getInstance()

  fun properties(vararg properties: Pair<String, String?>) {
    this.properties.putAll(properties)
  }

  fun variables(vararg variables: Pair<String, String?>) {
    this.variables.putAll(variables)
  }

  inline fun <R> withVariables(vararg variables: Pair<String, String?>, action: () -> R): R =
    useEnvironmentVariables(*variables, action = action)

  override fun property(name: String): String? {
    return when (name) {
      in properties -> properties[name]
      else -> previousEnvironment.property(name)
    }
  }

  override fun variable(name: String): String? {
    return when (name) {
      in variables -> variables[name]
      else -> previousEnvironment.variable(name)
    }
  }

  companion object {

    inline fun <R> useEnvironmentVariables(vararg variables: Pair<String, String?>, action: () -> R): R {
      val environment = TestEnvironment()
      environment.variables(*variables)
      application.useService(Environment::class.java, environment) {
        return action()
      }
    }

    inline fun <T : Any, R> ComponentManager.useService(serviceInterface: Class<T>, service: T, action: () -> R): R {
      Disposer.newDisposable().use { parentDisposable ->
        replaceService(serviceInterface, service, parentDisposable)
        return action()
      }
    }
  }
}