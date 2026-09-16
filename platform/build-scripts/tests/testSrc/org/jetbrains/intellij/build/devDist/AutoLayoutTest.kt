// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test

/** The members an `auto` layout takes from the main module's dependency group; see [autoLayoutChildren]. */
class AutoLayoutTest {
  @Test
  fun `a direct production dependency in the main module's group is a child`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val main = project.addModule("intellij.demo.plugin", JpsJavaModuleType.INSTANCE)
    // The `.plugin` suffix is not part of the group, so `intellij.demo.core` is a child of `intellij.demo.plugin`.
    main.dependOn(project.addModule("intellij.demo.core", JpsJavaModuleType.INSTANCE))
    main.dependOn(project.addModule("intellij.demo.ui", JpsJavaModuleType.INSTANCE))
    main.dependOn(project.addModule("intellij.other", JpsJavaModuleType.INSTANCE))
    main.dependOn(project.addModule("intellij.demo.testFramework", JpsJavaModuleType.INSTANCE), scope = JpsJavaDependencyScope.TEST)
    main.dependOn(project.addModule("intellij.demo.platform", JpsJavaModuleType.INSTANCE))

    val children = autoLayoutChildren(module = main, isPackedElsewhere = { it == "intellij.demo.platform" })

    // Sorted, and without the other group, the test dependency and the module the platform packs.
    assertThat(children).containsExactly("intellij.demo.core", "intellij.demo.ui")
  }

  @Test
  fun `a transitive dependency is not a child`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val main = project.addModule("intellij.demo", JpsJavaModuleType.INSTANCE)
    val core = project.addModule("intellij.demo.core", JpsJavaModuleType.INSTANCE)
    main.dependOn(core)
    core.dependOn(project.addModule("intellij.demo.util", JpsJavaModuleType.INSTANCE))

    assertThat(autoLayoutChildren(module = main, isPackedElsewhere = { false })).containsExactly("intellij.demo.core")
  }
}

private fun JpsModule.dependOn(other: JpsModule, scope: JpsJavaDependencyScope = JpsJavaDependencyScope.COMPILE) {
  val dependency = dependenciesList.addModuleDependency(other)
  JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = scope
}
