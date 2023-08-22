// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil.addDependency
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class RemoveDuplicatingDependenciesTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `duplicating items`() {
    val module = projectModel.createModule()
    check(listOf(module, module), module)
  }

  @Test
  fun `honor scope and exported flag`() {
    val a = projectModel.createModule("a")
    val b = projectModel.createModule("b")
    val c = projectModel.createModule("c")
    val d = projectModel.createModule("d")
    val e = projectModel.createModule("e")
    addDependency(a, b)
    addDependency(a, c, DependencyScope.RUNTIME, false)
    addDependency(a, d, DependencyScope.COMPILE, true)
    check(listOf(a, b, c, d, e), a, b, c, e)
  }

  @Test
  fun `transitive dependencies`() {
    val a = projectModel.createModule("a")
    val b1 = projectModel.createModule("b1")
    val b2 = projectModel.createModule("b2")
    val c1 = projectModel.createModule("c1")
    val c2 = projectModel.createModule("c2")
    addDependency(a, b1, DependencyScope.COMPILE, true)
    addDependency(b1, b2, DependencyScope.COMPILE, true)
    addDependency(a, c1, DependencyScope.COMPILE, true)
    addDependency(c1, c2)
    check(listOf(a, b2, c2), a, c2)
  }

  @Test
  fun `simple cycle`() {
    val a = projectModel.createModule("a")
    val b = projectModel.createModule("b")
    val c = projectModel.createModule("c")
    addDependency(a, b, DependencyScope.COMPILE, true)
    addDependency(b, c)
    addDependency(c, a, DependencyScope.COMPILE, true)
    check(listOf(a, b, c), c)
  }
  @Test
  fun `dominated cycle`() {
    val a = projectModel.createModule("a")
    val b1 = projectModel.createModule("b1")
    val b2 = projectModel.createModule("b2")
    val b3 = projectModel.createModule("b3")
    val c = projectModel.createModule("c")
    addDependency(a, b1, DependencyScope.COMPILE, true)
    addDependency(b1, b2, DependencyScope.COMPILE, true)
    addDependency(b2, b3, DependencyScope.COMPILE, true)
    addDependency(b3, b1, DependencyScope.COMPILE, true)
    addDependency(b3, c, DependencyScope.COMPILE, true)
    check(listOf(a, b1, b2, b3, c), a)
  }

  @Test
  fun `cycle with dependency`() {
    val b1 = projectModel.createModule("b1")
    val b2 = projectModel.createModule("b2")
    val b3 = projectModel.createModule("b3")
    val c = projectModel.createModule("c")
    addDependency(b1, b2, DependencyScope.COMPILE, true)
    addDependency(b2, b3, DependencyScope.COMPILE, true)
    addDependency(b3, b1, DependencyScope.COMPILE, true)
    addDependency(b3, c, DependencyScope.COMPILE, true)
    check(listOf(b1, b2, b3, c), b1)
  }

  @Test
  fun `cycle and separate item`() {
    val a = projectModel.createModule("a")
    val b1 = projectModel.createModule("b1")
    val b2 = projectModel.createModule("b2")
    val b3 = projectModel.createModule("b3")
    addDependency(b1, b2, DependencyScope.COMPILE, true)
    addDependency(b2, b3, DependencyScope.COMPILE, true)
    addDependency(b3, b1, DependencyScope.COMPILE, true)
    check(listOf(a, b1, b2, b3), a, b1)
  }

  private fun check(dependencies: List<Module>, vararg module: Module) {
    assertThat(JavaProjectDependenciesAnalyzer.removeDuplicatingDependencies(dependencies)).containsExactlyInAnyOrder(*module)
  }
}