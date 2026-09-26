// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.ProductProperties
import org.junit.jupiter.api.Test
import java.nio.file.Path

private const val CLASS_NAME = "org.example.ExampleProperties"
private const val DEFAULT_ROOT_MODULE = "intellij.example.product.modules"
private const val WRAPPER_ROOT_MODULE = "intellij.example.wrapper.product.modules"

/**
 * The `rootModule` of a `build/dev-build.json` key replaces the root module of the modular loader, and the distribution
 * keeps the default root module of the class. A product without a modular loader cannot take the key.
 */
class DevBuildRootModuleTest {
  @Test
  fun `a root module replaces the default and joins the implementation modules`() {
    val properties = ExampleProperties(rootModule = DEFAULT_ROOT_MODULE)

    applyRootModule(properties = properties, rootModule = WRAPPER_ROOT_MODULE, className = CLASS_NAME)

    assertThat(properties.rootModuleForModularLoader).isEqualTo(WRAPPER_ROOT_MODULE)
    assertThat(properties.productLayout.productImplementationModules).containsExactly("intellij.platform.starter", DEFAULT_ROOT_MODULE, WRAPPER_ROOT_MODULE)
  }

  @Test
  fun `a product without a modular loader rejects the root module`() {
    val properties = ExampleProperties(rootModule = null)

    assertThatThrownBy { applyRootModule(properties = properties, rootModule = WRAPPER_ROOT_MODULE, className = CLASS_NAME) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(CLASS_NAME)
      .hasMessageContaining("rootModuleForModularLoader")
    assertThat(properties.rootModuleForModularLoader).isNull()
    assertThat(properties.productLayout.productImplementationModules).containsExactly("intellij.platform.starter")
  }
}

private class ExampleProperties(rootModule: String?) : ProductProperties() {
  init {
    rootModuleForModularLoader = rootModule
    productLayout.productImplementationModules = listOfNotNull("intellij.platform.starter", rootModule)
  }

  override val baseFileName: String = "example"
  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "example"
  override fun createWindowsCustomizer(projectHome: Path) = null
  override fun createLinuxCustomizer(projectHome: Path) = null
  override fun createMacCustomizer(projectHome: Path) = null
  override fun getProductContentDescriptor() = null
}
