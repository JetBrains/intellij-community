// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.platform.runtime.product.ProductMode
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class JpsProductModeMatcherTest {
  @Test
  fun `does not expose in-progress match result to concurrent callers`() {
    val rootModule = createModuleWithSlowIncompatibleDependencyCheck()
    val executor = Executors.newFixedThreadPool(8)
    try {
      repeat(20) {
        val matcher = JpsProductModeMatcher(ProductMode.FRONTEND)
        val start = CountDownLatch(1)
        val results = List(8) {
          executor.submit<Boolean> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue()
            matcher.matches(rootModule)
          }
        }

        start.countDown()

        for (result in results) {
          assertThat(result.get(5, TimeUnit.SECONDS)).isFalse()
        }
      }
    }
    finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun `checks independent root modules concurrently`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val rootModules = List(8) {
      addModuleWithSlowIncompatibleDependencyCheck(project, "intellij.test.root.$it", compatibleDependencyCount = 500)
    }
    val matcher = JpsProductModeMatcher(ProductMode.FRONTEND)
    val executor = Executors.newFixedThreadPool(rootModules.size)
    try {
      val start = CountDownLatch(1)
      val results = rootModules.map { rootModule ->
        executor.submit<Boolean> {
          assertThat(start.await(5, TimeUnit.SECONDS)).isTrue()
          matcher.matches(rootModule)
        }
      }

      start.countDown()

      for (result in results) {
        assertThat(result.get(5, TimeUnit.SECONDS)).isFalse()
      }
    }
    finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun `handles circular dependencies`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val firstModule = project.addModule("intellij.test.first", JpsJavaModuleType.INSTANCE)
    val secondModule = project.addModule("intellij.test.second", JpsJavaModuleType.INSTANCE)
    firstModule.dependenciesList.addModuleDependency(secondModule)
    secondModule.dependenciesList.addModuleDependency(firstModule)

    val matcher = JpsProductModeMatcher(ProductMode.FRONTEND)

    assertThat(matcher.matches(firstModule)).isTrue()
    assertThat(matcher.matches(secondModule)).isTrue()
  }

  @Test
  fun `does not cache incomplete positive result from circular dependency`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val firstModule = project.addModule("intellij.test.first", JpsJavaModuleType.INSTANCE)
    val secondModule = project.addModule("intellij.test.second", JpsJavaModuleType.INSTANCE)
    firstModule.dependenciesList.addModuleDependency(secondModule)
    firstModule.dependenciesList.addModuleDependency(project.addModule("intellij.platform.backend", JpsJavaModuleType.INSTANCE))
    secondModule.dependenciesList.addModuleDependency(firstModule)

    val matcher = JpsProductModeMatcher(ProductMode.FRONTEND)

    assertThat(matcher.matches(firstModule)).isFalse()
    assertThat(matcher.matches(secondModule)).isFalse()
  }

  private fun createModuleWithSlowIncompatibleDependencyCheck(): JpsModule {
    val project = JpsElementFactory.getInstance().createModel().project
    return addModuleWithSlowIncompatibleDependencyCheck(project, "intellij.test.root", compatibleDependencyCount = 2_000)
  }

  private fun addModuleWithSlowIncompatibleDependencyCheck(
    project: JpsProject,
    rootModuleName: String,
    compatibleDependencyCount: Int,
  ): JpsModule {
    val rootModule = project.addModule(rootModuleName, JpsJavaModuleType.INSTANCE)
    repeat(compatibleDependencyCount) {
      rootModule.dependenciesList.addModuleDependency(project.addModule("$rootModuleName.compatible.$it", JpsJavaModuleType.INSTANCE))
    }
    rootModule.dependenciesList.addModuleDependency(project.addModule("intellij.platform.backend", JpsJavaModuleType.INSTANCE))
    return rootModule
  }
}
