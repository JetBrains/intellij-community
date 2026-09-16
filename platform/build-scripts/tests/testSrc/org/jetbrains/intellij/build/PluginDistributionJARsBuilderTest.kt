// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.createIdeClassPath
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

class PluginDistributionJARsBuilderTest {
  @Test
  @Timeout(30)
  @Suppress("DEPRECATION")
  fun sourceLayoutDoesNotRegisterDistFiles() {
    BuildLifetime().use { lifetime ->
      val properties = object : IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot) {
        override fun registerDistFiles(context: BuildContext) {
          error("Source layout must not register distribution files")
        }
      }
      properties.ijentDistributionRegistrar = { error("Source layout must not register IJent files") }
      val context = createBuildContext(COMMUNITY_ROOT.communityRoot, properties, lifetime = lifetime)

      val layout = createPlatformLayout(productProperties = properties, outputProvider = context.outputProvider)

      assertThat(layout.includedModules).isNotEmpty()
      assertThat(context.getDistFiles(os = null, arch = null, libcImpl = null)).isEmpty()
    }
  }

  @Test
  @Suppress("DEPRECATION")
  fun verifyStableClasspathOrder(): Unit = BuildLifetime().use { lifetime ->
    runBlocking(Dispatchers.Default) {
      val context = createBuildContext(COMMUNITY_ROOT.communityRoot, IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot), lifetime = lifetime)
      val ideClasspath1 = createIdeClassPath(createPlatformLayout(context = context), context)
      val ideClasspath2 = createIdeClassPath(createPlatformLayout(context = context), context)
      assertThat(ideClasspath1).isEqualTo(ideClasspath2)
    }
  }
}
