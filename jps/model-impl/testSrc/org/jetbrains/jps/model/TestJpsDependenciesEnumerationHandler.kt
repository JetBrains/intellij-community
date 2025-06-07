// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model

import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler
import org.jetbrains.jps.model.module.JpsModule
import java.io.Closeable
import java.util.*

data class TestJpsDependenciesEnumerationHandler(
  val includeTestsFromDependentModulesToTestClasspath: Boolean,
) : JpsJavaDependenciesEnumerationHandler() {
  override fun shouldIncludeTestsFromDependentModulesToTestClasspath() =
    includeTestsFromDependentModulesToTestClasspath

  companion object {
    private val handlers = Collections.synchronizedMap<JpsModule, TestJpsDependenciesEnumerationHandler>(
      HashMap<JpsModule, TestJpsDependenciesEnumerationHandler>()
    )

    fun addModule(module: JpsModule, includeTestsFromDependentModulesToTestClasspath: Boolean): Closeable {
      val old = handlers.put(
        module,
        TestJpsDependenciesEnumerationHandler(includeTestsFromDependentModulesToTestClasspath))
      check(old == null) {
        "Trying to re-register handler for module ${module.name}"
      }

      return Closeable {
        val old = handlers.remove(module)
        check(old != null) {
          "Module ${module.name} must be present"
        }
      }
    }

    class Factory : JpsJavaDependenciesEnumerationHandler.Factory() {
      override fun createHandler(modules: Collection<JpsModule>): JpsJavaDependenciesEnumerationHandler? {
        require(modules.isNotEmpty())

        val handlers = modules.map { handlers[it] }
        val first = handlers.first()

        handlers.forEach {
          check(it == first) {
            "All handlers must be the same"
          }
        }

        return first
      }
    }
  }
}