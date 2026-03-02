// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.discovery.ContentModuleInfo
import org.jetbrains.intellij.build.productLayout.discovery.LegacyDepends
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginSource
import org.jetbrains.intellij.build.productLayout.generator.collectPluginGraphDeps
import org.jetbrains.intellij.build.productLayout.generator.filterPluginDependencies
import org.jetbrains.intellij.build.productLayout.graph.PluginGraphBuilder
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PluginDependencyGraphTest {
  @Test
  fun `dependsOnPlugin edges are created and traversable`() {
    val graph = buildGraph(
      TargetName("plugin.a") to pluginInfo(
        pluginId = "com.a",
        pluginDependencies = setOf(PluginId("com.b")),
      ),
      TargetName("plugin.b") to pluginInfo("com.b"),
    )

    graph.query {
      val pluginA = requireNotNull(plugin("plugin.a"))
      val deps = mutableListOf<Pair<String, Boolean>>()
      val modernFlags = mutableListOf<Boolean>()
      val legacyFlags = mutableListOf<Boolean>()
      val configFileFlags = mutableListOf<Boolean>()
      pluginA.dependsOnPlugin { dep ->
        deps.add(dep.target().name().value to dep.isOptional)
        modernFlags.add(dep.hasModernFormat)
        legacyFlags.add(dep.hasLegacyFormat)
        configFileFlags.add(dep.hasConfigFile)
      }
      assertThat(deps).containsExactlyInAnyOrder("plugin.b" to false)
      assertThat(modernFlags).containsExactly(true)
      assertThat(legacyFlags).containsExactly(false)
      assertThat(configFileFlags).containsExactly(false)

      val pluginB = requireNotNull(plugin("plugin.b"))
      val requiredBy = mutableListOf<String>()
      pluginB.requiredByPlugin { plugin -> requiredBy.add(plugin.name().value) }
      assertThat(requiredBy).contains("plugin.a")
    }

    val ids = graph.getPluginDependencies(TargetName("plugin.a"))
    assertThat(ids).containsExactlyInAnyOrder(PluginId("com.b"))
  }

  @Test
  fun `optional legacy depends are marked optional and excluded by default`() {
    val graph = buildGraph(
      TargetName("plugin.a") to pluginInfo(
        pluginId = "com.a",
        legacyDepends = listOf(LegacyDepends(pluginId = PluginId("com.b"), optional = true)),
      ),
      TargetName("plugin.b") to pluginInfo("com.b"),
    )

    graph.query {
      val pluginA = requireNotNull(plugin("plugin.a"))
      val optionalFlags = mutableListOf<Boolean>()
      val modernFlags = mutableListOf<Boolean>()
      val legacyFlags = mutableListOf<Boolean>()
      val configFileFlags = mutableListOf<Boolean>()
      pluginA.dependsOnPlugin { dep ->
        optionalFlags.add(dep.isOptional)
        modernFlags.add(dep.hasModernFormat)
        legacyFlags.add(dep.hasLegacyFormat)
        configFileFlags.add(dep.hasConfigFile)
      }
      assertThat(optionalFlags).containsExactly(true)
      assertThat(modernFlags).containsExactly(false)
      assertThat(legacyFlags).containsExactly(true)
      assertThat(configFileFlags).containsExactly(false)
    }

    assertThat(graph.getPluginDependencies(TargetName("plugin.a"))).isEmpty()
    assertThat(graph.getPluginDependencies(TargetName("plugin.a"), includeOptional = true))
      .containsExactlyInAnyOrder(PluginId("com.b"))
  }

  @Test
  fun `config-file legacy depends are treated as optional`() {
    val graph = buildGraph(
      TargetName("plugin.a") to pluginInfo(
        pluginId = "com.a",
        legacyDepends = listOf(LegacyDepends(
          pluginId = PluginId("com.b"),
          optional = false,
          configFile = "used.xml",
        )),
      ),
      TargetName("plugin.b") to pluginInfo("com.b"),
    )

    graph.query {
      val pluginA = requireNotNull(plugin("plugin.a"))
      val optionalFlags = mutableListOf<Boolean>()
      val modernFlags = mutableListOf<Boolean>()
      val legacyFlags = mutableListOf<Boolean>()
      val configFileFlags = mutableListOf<Boolean>()
      pluginA.dependsOnPlugin { dep ->
        optionalFlags.add(dep.isOptional)
        modernFlags.add(dep.hasModernFormat)
        legacyFlags.add(dep.hasLegacyFormat)
        configFileFlags.add(dep.hasConfigFile)
      }
      assertThat(optionalFlags).containsExactly(true)
      assertThat(modernFlags).containsExactly(false)
      assertThat(legacyFlags).containsExactly(true)
      assertThat(configFileFlags).containsExactly(true)
    }

    assertThat(graph.getPluginDependencies(TargetName("plugin.a"))).isEmpty()
    assertThat(graph.getPluginDependencies(TargetName("plugin.a"), includeOptional = true))
      .containsExactlyInAnyOrder(PluginId("com.b"))
  }

  @Test
  fun `collectPluginGraphDeps captures config-file legacy deps separately`() {
    val graph = pluginGraph {
      plugin("plugin.a") {
        pluginId("com.a")
        dependsOnLegacyPlugin("com.b", hasConfigFile = true)
      }
      plugin("plugin.b") {
        pluginId("com.b")
      }
      target("plugin.a") {
        dependsOn("plugin.b")
      }
      linkPluginMainTarget("plugin.a")
      linkPluginMainTarget("plugin.b")
    }

    val graphDeps = collectPluginGraphDeps(
      graph = graph,
      allRealProductNames = emptySet(),
      libraryModuleFilter = { true },
    )
      .single { it.pluginContentModuleName == ContentModuleName("plugin.a") }

    assertThat(graphDeps.jpsPluginDependencies).containsExactly(PluginId("com.b"))
    assertThat(graphDeps.legacyConfigFilePluginDependencies).containsExactly(PluginId("com.b"))
  }

  @Test
  fun `filterPluginDependencies excludes jps deps covered by legacy config-file depends`() {
    val graph = pluginGraph {
      plugin("plugin.a") {
        pluginId("com.a")
        dependsOnLegacyPlugin("com.b", hasConfigFile = true)
      }
      plugin("plugin.b") {
        pluginId("com.b")
      }
      target("plugin.a") {
        dependsOn("plugin.b")
      }
      linkPluginMainTarget("plugin.a")
      linkPluginMainTarget("plugin.b")
    }

    val graphDeps = collectPluginGraphDeps(
      graph = graph,
      allRealProductNames = emptySet(),
      libraryModuleFilter = { true },
    )
      .single { it.pluginContentModuleName == ContentModuleName("plugin.a") }

    val filtered = filterPluginDependencies(
      graphDeps = graphDeps,
      pluginInfo = pluginInfo("com.a"),
      jpsPluginDependencies = graphDeps.jpsPluginDependencies - graphDeps.legacyConfigFilePluginDependencies,
      suppressedModules = emptySet(),
      suppressedPlugins = emptySet(),
    )

    assertThat(filtered.pluginDependencies).isEmpty()
  }

  @Test
  fun `legacy and modern formats are both recorded`() {
    val graph = buildGraph(
      TargetName("plugin.a") to pluginInfo(
        pluginId = "com.a",
        pluginDependencies = setOf(PluginId("com.b")),
        legacyDepends = listOf(LegacyDepends(pluginId = PluginId("com.b"))),
      ),
      TargetName("plugin.b") to pluginInfo("com.b"),
    )

    graph.query {
      val pluginA = requireNotNull(plugin("plugin.a"))
      val legacyFlags = mutableListOf<Boolean>()
      val modernFlags = mutableListOf<Boolean>()
      val configFileFlags = mutableListOf<Boolean>()
      pluginA.dependsOnPlugin { dep ->
        legacyFlags.add(dep.hasLegacyFormat)
        modernFlags.add(dep.hasModernFormat)
        configFileFlags.add(dep.hasConfigFile)
      }
      assertThat(legacyFlags).containsExactly(true)
      assertThat(modernFlags).containsExactly(true)
      assertThat(configFileFlags).containsExactly(false)
    }
  }

  @Test
  fun `dependency on alias stays as alias id`() {
    val graph = buildGraph(
      TargetName("plugin.a") to pluginInfo(
        pluginId = "com.a",
        pluginDependencies = setOf(PluginId("alias.c")),
      ),
      TargetName("plugin.c") to pluginInfo(
        pluginId = "com.c",
        pluginAliases = listOf(PluginId("alias.c")),
      ),
    )

    graph.query {
      val pluginA = requireNotNull(plugin("plugin.a"))
      val deps = mutableListOf<String>()
      pluginA.dependsOnPlugin { dep -> deps.add(dep.target().name().value) }
      assertThat(deps).containsExactlyInAnyOrder("alias.c")
    }

    val ids = graph.getPluginDependencies(TargetName("plugin.a"))
    assertThat(ids).containsExactlyInAnyOrder(PluginId("alias.c"))
  }

  @Test
  fun `discovered plugin content is added to graph`() {
    runBlocking(Dispatchers.Default) {
      val targetModule = TargetName("plugin.b")
      val moduleInfo = ContentModuleInfo(ContentModuleName("plugin.b.module"), ModuleLoadingRuleValue.REQUIRED)
      val info = pluginInfo(
        pluginId = "com.b",
        contentModules = listOf(moduleInfo),
        source = PluginSource.DISCOVERED,
      )

      val builder = PluginGraphBuilder()
      builder.addTarget(targetModule)
      builder.registerReferencedPlugins(object : PluginContentProvider {
        override suspend fun getOrExtract(pluginModule: TargetName): PluginContentInfo? {
          return if (pluginModule == targetModule) info else null
        }
      })

      val graph = builder.build()

      graph.query {
        val plugin = requireNotNull(plugin("plugin.b"))
        val contentNames = mutableListOf<String>()
        plugin.containsContent { module, _ -> contentNames.add(module.name().value) }
        assertThat(contentNames).containsExactly("plugin.b.module")

        val module = requireNotNull(contentModule(ContentModuleName("plugin.b.module")))
        var loading: ModuleLoadingRuleValue? = null
        plugin.containsContent { contentModule, mode ->
          if (contentModule.id == module.id) {
            loading = mode
          }
        }
        assertThat(loading).isEqualTo(ModuleLoadingRuleValue.REQUIRED)

        val mainTargets = mutableListOf<String>()
        plugin.mainTarget { target -> mainTargets.add(target.name()) }
        assertThat(mainTargets).containsExactly("plugin.b")

        val backedBy = mutableListOf<String>()
        module.backedBy { target -> backedBy.add(target.name()) }
        assertThat(backedBy).containsExactly("plugin.b.module")
      }
    }
  }

  @Test
  fun `discovered plugin id resolves to content node`() {
    runBlocking(Dispatchers.Default) {
      val discoveredModule = TargetName("plugin.b.module")
      val discoveredContent = ContentModuleInfo(ContentModuleName("plugin.b.content"), ModuleLoadingRuleValue.REQUIRED)
      val discoveredInfo = pluginInfo(
        pluginId = "com.b",
        contentModules = listOf(discoveredContent),
        source = PluginSource.DISCOVERED,
      )
      val dependentInfo = pluginInfo(
        pluginId = "com.a",
        pluginDependencies = setOf(PluginId("com.b")),
      )

      val builder = PluginGraphBuilder()
      builder.addTarget(discoveredModule)
      val discoveredInfos = builder.registerReferencedPlugins(object : PluginContentProvider {
        override suspend fun getOrExtract(pluginModule: TargetName): PluginContentInfo? {
          return if (pluginModule == discoveredModule) discoveredInfo else null
        }
      })

      val pluginInfos = linkedMapOf(
        TargetName("plugin.a") to dependentInfo,
      )
      for ((pluginModule, info) in discoveredInfos) {
        pluginInfos[pluginModule] = info
      }

      builder.addPluginDependencyEdges(pluginInfos)
      val graph = builder.build()

      graph.query {
        val pluginA = requireNotNull(plugin("plugin.a"))
        val depNames = mutableListOf<String>()
        val depContent = mutableListOf<String>()
        pluginA.dependsOnPlugin { dep ->
          val target = dep.target()
          depNames.add(target.name().value)
          target.containsContent { module, _ -> depContent.add(module.name().value) }
        }
        assertThat(depNames).containsExactly("plugin.b.module")
        assertThat(depContent).containsExactly("plugin.b.content")
      }
    }
  }

  private fun buildGraph(vararg plugins: Pair<TargetName, PluginContentInfo>): PluginGraph {
    val builder = PluginGraphBuilder()
    val testFrameworkModules = emptySet<ContentModuleName>()

    for ((moduleName, info) in plugins) {
      builder.addPluginWithContent(moduleName, info, testFrameworkModules)
    }
    builder.addPluginDependencyEdges(plugins.toMap())

    return builder.build()
  }

  private fun pluginInfo(
    pluginId: String,
    contentModules: List<ContentModuleInfo> = emptyList(),
    pluginDependencies: Set<PluginId> = emptySet(),
    legacyDepends: List<LegacyDepends> = emptyList(),
    pluginAliases: List<PluginId> = emptyList(),
    source: PluginSource = PluginSource.BUNDLED,
  ): PluginContentInfo {
    return PluginContentInfo(
      pluginXmlPath = Path.of("/tmp/$pluginId/plugin.xml"),
      pluginXmlContent = "",
      pluginId = PluginId(pluginId),
      contentModules = contentModules,
      pluginDependencies = pluginDependencies,
      legacyDepends = legacyDepends,
      pluginAliases = pluginAliases,
      source = source,
    )
  }
}
