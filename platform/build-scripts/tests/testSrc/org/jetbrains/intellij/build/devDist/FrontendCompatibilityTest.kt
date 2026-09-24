// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test

/**
 * [FrontendCompatibility] over a graph with a cycle.
 *
 * `a` and `b` reach each other, and `b` also reaches the root `backend`, so neither is compatible. `c` and `d` form a
 * cycle that reaches no root, so both are compatible. The answer must not depend on the module a walk starts from.
 */
class FrontendCompatibilityTest {
  @Test
  fun `a cycle takes the answer of the module it reaches`() {
    val project = project()
    val frontend = FrontendCompatibility(roots = setOf("backend"), findModule = project::findModuleByName)

    assertThat(frontend.isCompatible("a")).isFalse()
    assertThat(frontend.isCompatible("b")).isFalse()
    assertThat(frontend.isCompatible("c")).isTrue()
    assertThat(frontend.isCompatible("d")).isTrue()
    assertThat(frontend.isSplit(mainModule = "a", member = "c")).isTrue()
  }

  @Test
  fun `concurrent callers of one instance get the answers of a fresh instance`() {
    val project = project()
    val modules = listOf("a", "b", "c", "d", "backend")
    val expected = modules.associateWith { FrontendCompatibility(roots = setOf("backend"), findModule = project::findModuleByName).isCompatible(it) }

    val shared = FrontendCompatibility(roots = setOf("backend"), findModule = project::findModuleByName)
    val asked = (1..200).flatMap { round -> if (round % 2 == 0) modules else modules.reversed() }
    val answers = asked.mapConcurrent { shared.isCompatible(it) }

    assertThat(asked.zip(answers)).allSatisfy { (module, answer) -> assertThat(answer).isEqualTo(expected.getValue(module)) }
  }

  private fun project(): JpsProject {
    val project = JpsElementFactory.getInstance().createModel().project
    val modules = listOf("a", "b", "c", "d", "backend").associateWith { project.addModule(it, JpsJavaModuleType.INSTANCE) }
    fun JpsModule.dependsOn(name: String) {
      dependenciesList.addModuleDependency(modules.getValue(name))
    }
    modules.getValue("a").dependsOn("b")
    modules.getValue("b").dependsOn("a")
    modules.getValue("b").dependsOn("backend")
    modules.getValue("c").dependsOn("d")
    modules.getValue("d").dependsOn("c")
    return project
  }
}
