// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression
import org.jetbrains.intellij.build.productLayout.config.PluginSuppression
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.InvalidSuppressionConfigKeyError
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(TestFailureLogger::class)
class SuppressionConfigValidatorTest {

  @Nested
  inner class MisplacedKeyDetection {

    @Test
    fun `detects content module wrongly placed in plugins section`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        plugin("my.plugin") {
          content("my.content.module")
        }
      }

      // Content module "my.content.module" wrongly added to plugins section
      val suppressionConfig = SuppressionConfig(
        plugins = mapOf(
          ContentModuleName("my.content.module") to PluginSuppression(
            suppressModules = setOf(ContentModuleName("some.dep")),
          ),
        ),
      )

      val model = testGenerationModel(graph, suppressionConfig = suppressionConfig)
      val errors = runValidationRule(SuppressionConfigValidator, model)

      assertThat(errors).hasSize(1)
      assertThat(errors[0]).isInstanceOf(InvalidSuppressionConfigKeyError::class.java)
      val error = errors[0] as InvalidSuppressionConfigKeyError
      assertThat(error.invalidPluginKeys).containsExactly(ContentModuleName("my.content.module"))
      assertThat(error.misplacedInPlugins).containsExactly(ContentModuleName("my.content.module"))

      val formattedMessage = error.format(AnsiStyle(useAnsi = false))
      assertThat(formattedMessage).contains("This is a content module")
      assertThat(formattedMessage).contains("Move to 'contentModules' section")
    }

    @Test
    fun `detects plugin wrongly placed in contentModules section`(): Unit = runBlocking(Dispatchers.Default) {
      val graph = pluginGraph {
        plugin("my.plugin") {
          content("my.module")
        }
      }

      // Plugin "my.plugin" wrongly added to contentModules section
      val suppressionConfig = SuppressionConfig(
        contentModules = mapOf(
          ContentModuleName("my.plugin") to ContentModuleSuppression(
            suppressModules = setOf(ContentModuleName("some.dep")),
          ),
        ),
      )

      val model = testGenerationModel(graph, suppressionConfig = suppressionConfig)
      val errors = runValidationRule(SuppressionConfigValidator, model)

      assertThat(errors).hasSize(1)
      assertThat(errors[0]).isInstanceOf(InvalidSuppressionConfigKeyError::class.java)
      val error = errors[0] as InvalidSuppressionConfigKeyError
      assertThat(error.invalidContentModuleKeys).containsExactly(ContentModuleName("my.plugin"))
      assertThat(error.misplacedInContentModules).containsExactly(ContentModuleName("my.plugin"))

      val formattedMessage = error.format(AnsiStyle(useAnsi = false))
      assertThat(formattedMessage).contains("This is a plugin")
      assertThat(formattedMessage).contains("Move to 'plugins' section")
    }
  }
}
