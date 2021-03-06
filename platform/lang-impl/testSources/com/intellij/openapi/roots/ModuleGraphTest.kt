// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ModuleGraphTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `module graph`() {
    val a = projectModel.createModule("a")
    val b = projectModel.createModule("b")
    val c = projectModel.createModule("c")
    ModuleRootModificationUtil.addDependency(a, b)
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.TEST, false)
    val graph = projectModel.moduleManager.moduleGraph()
    assertThat(graph.nodes).containsExactlyInAnyOrder(a, b, c)
    assertThat(graph.getIn(a).asSequence().toList()).containsExactlyInAnyOrder(b, c)
    assertThat(graph.getOut(b).asSequence().toList()).containsExactly(a)
    assertThat(graph.getOut(c).asSequence().toList()).containsExactly(a)
    assertThat(graph.getIn(b)).isExhausted
    assertThat(graph.getIn(c)).isExhausted
    assertThat(graph.getOut(a)).isExhausted
  }

  @Test
  fun `module graph without tests`() {
    val a = projectModel.createModule("a")
    val b = projectModel.createModule("b")
    val c = projectModel.createModule("c")
    ModuleRootModificationUtil.addDependency(a, b)
    ModuleRootModificationUtil.addDependency(a, c, DependencyScope.TEST, false)
    val graph = projectModel.moduleManager.moduleGraph(false)
    assertThat(graph.nodes).containsExactlyInAnyOrder(a, b, c)
    assertThat(graph.getIn(a).asSequence().toList()).containsExactly(b)
    assertThat(graph.getIn(b)).isExhausted
    assertThat(graph.getIn(c)).isExhausted
  }

  @Test
  fun `dependency comparator`() {
    val a = projectModel.createModule("a")
    val b = projectModel.createModule("b")
    val c = projectModel.createModule("c")
    ModuleRootModificationUtil.addDependency(a, b)
    ModuleRootModificationUtil.addDependency(b, c, DependencyScope.TEST, false)
    val comparator = projectModel.moduleManager.moduleDependencyComparator()
    assertThat(comparator.compare(a, b)).isPositive
    assertThat(comparator.compare(b, c)).isPositive
  }

  @Test
  fun `clear cache`() {
    val a = projectModel.createModule("a")
    val graph = projectModel.moduleManager.moduleGraph()
    assertThat(graph.nodes).containsExactly(a)
    val b = projectModel.createModule("b")
    val graph2 = projectModel.moduleManager.moduleGraph()
    assertThat(graph2.nodes).containsExactlyInAnyOrder(a, b)
  }
}