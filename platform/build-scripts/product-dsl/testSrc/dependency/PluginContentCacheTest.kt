// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.TargetName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.discovery.PluginXmlOverride
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class PluginContentCacheTest {
  @Test
  fun `extract uses plugin xml override instead of stale source descriptor`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val jps = jpsProject(tempDir) {
        module("intellij.product.plugin") {
          resourceRoot = "resources"
        }
      }

      val stalePluginXmlPath = tempDir.resolve("intellij/product/plugin/resources/META-INF/plugin.xml")
      Files.createDirectories(stalePluginXmlPath.parent)
      Files.writeString(
        stalePluginXmlPath,
        """
        <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
          <xi:include href="/META-INF/missing.xml"/>
          <content namespace="jetbrains">
            <module name="intellij.from.stale"/>
          </content>
        </idea-plugin>
        """.trimIndent(),
      )

      val pluginName = TargetName("intellij.product.plugin")
      val outputProvider = object : ModuleOutputProvider by createTestModuleOutputProvider(jps.project) {
        override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? = null
      }

      val staleErrorSink = ErrorSink()
      val staleCache = PluginContentCache(
        outputProvider = outputProvider,
        xIncludeCache = AsyncCache(this),
        skipXIncludePaths = emptySet(),
        xIncludePrefixFilter = { null },
        scope = this,
        errorSink = staleErrorSink,
      )
      val staleContent = staleCache.extract(plugin = pluginName, isTest = false)

      assertThat(staleContent).isNotNull
      assertThat(staleErrorSink.getErrors()).isNotEmpty

      val overrideErrorSink = ErrorSink()
      val overrideCache = PluginContentCache(
        outputProvider = outputProvider,
        xIncludeCache = AsyncCache(this),
        skipXIncludePaths = emptySet(),
        xIncludePrefixFilter = { null },
        scope = this,
        errorSink = overrideErrorSink,
      )
      val overrideContent = overrideCache.extract(
        plugin = pluginName,
        isTest = false,
        pluginXmlOverride = PluginXmlOverride(
          pluginXmlPath = stalePluginXmlPath,
          pluginXmlContent = """
          <idea-plugin>
            <content namespace="jetbrains">
              <module name="intellij.from.override"/>
            </content>
          </idea-plugin>
          """.trimIndent(),
        ),
      )

      assertThat(overrideContent).isNotNull
      assertThat(overrideContent!!.contentModules.map { it.name.value }).containsExactly("intellij.from.override")
      assertThat(overrideErrorSink.getErrors()).isEmpty()
    }
  }
}
