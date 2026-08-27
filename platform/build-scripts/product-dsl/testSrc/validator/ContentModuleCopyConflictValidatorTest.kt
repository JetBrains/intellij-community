// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginGraph.toActualId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.ContentModuleCopyConflictException
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.GraphPluginBuilder
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleCopyConflictError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

/**
 * Tests for [ContentModuleCopyConflictValidator].
 *
 * The rule detects the shape of https://youtrack.jetbrains.com/issue/QD-15883. Each graph below models
 * a real shape of the repository. The names come from the plugins of that issue, so a reader can compare
 * a case with the tree.
 */
@ExtendWith(TestFailureLogger::class)
class ContentModuleCopyConflictValidatorTest {
  /**
   * Real shape: the QD-15883 diamond.
   *
   * `intellij.qodana`, `intellij.dfa.analysis.plugin` and `intellij.ml.llm` each embedded a private copy of
   * `intellij.libraries.qodana.sarif`. `intellij.jvm.dfa.analysis.inspections` reached the Qodana copy through
   * `intellij.qodana.sarif`, and the DFA copy through a `<plugin id="com.intellij.dfa.analysis"/>` dependency.
   * The two copies raised a `LinkageError` on `com.jetbrains.qodana.sarif.model.Result`.
   */
  @Test
  fun `reports a content module that reaches two embedded copies`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.qodana")
        bundlesPlugin("intellij.dfa.analysis.plugin")
        bundlesPlugin("intellij.ml.llm")
        bundlesPlugin("intellij.jvm.dfa.analysis")
      }
      plugin("intellij.qodana") {
        pluginId("org.intellij.qodana")
        embeddedCopy("intellij.libraries.qodana.sarif")
        content("intellij.qodana.sarif")
      }
      plugin("intellij.dfa.analysis.plugin") {
        pluginId("com.intellij.dfa.analysis")
        embeddedCopy("intellij.libraries.qodana.sarif")
      }
      plugin("intellij.ml.llm") {
        pluginId("com.intellij.ml.llm")
        embeddedCopy("intellij.libraries.qodana.sarif")
      }
      plugin("intellij.jvm.dfa.analysis") {
        pluginId("com.intellij.jvm.dfa.analysis")
        content("intellij.jvm.dfa.analysis.inspections")
      }
      linkContentModuleDeps("intellij.jvm.dfa.analysis.inspections", "intellij.qodana.sarif")
    }

    val errors = runCopyConflictRule(
      graph,
      listOf(descriptorPluginDependencies("intellij.jvm.dfa.analysis.inspections", "com.intellij.dfa.analysis")),
    )

    val conflicts = singleErrorConflicts(errors)
    // the third copy stays out, because no module of the fourth plugin reaches the LLM plugin
    assertThat(conflicts.map { it.module.value }).containsExactly("intellij.jvm.dfa.analysis.inspections")
    val conflict = conflicts.single()
    assertThat(conflict.duplicatedModule).isEqualTo(ContentModuleName("intellij.libraries.qodana.sarif"))

    // the owner list is sorted by plugin name, so the DFA plugin comes first
    assertThat(conflict.owners.map { it.plugin.value })
      .containsExactly("intellij.dfa.analysis.plugin", "intellij.qodana")
    // a copy without a namespace gets an implicit namespace from its owning plugin
    val declaredCopy = PluginModuleId("intellij.libraries.qodana.sarif", namespace = null)
    assertThat(conflict.owners.map { it.moduleId }).containsExactly(
      declaredCopy.toActualId(PluginId("com.intellij.dfa.analysis")),
      declaredCopy.toActualId(PluginId("org.intellij.qodana")),
    )

    // each owner carries the path that reaches its copy
    assertThat(conflict.owners[0].path).containsExactly(
      "intellij.jvm.dfa.analysis.inspections",
      "declares <plugin id=\"com.intellij.dfa.analysis\">",
    )
    assertThat(conflict.owners[1].path).containsExactly(
      "intellij.jvm.dfa.analysis.inspections",
      "depends on module intellij.qodana.sarif",
      "is a content module of plugin intellij.qodana",
    )
  }

  /**
   * Real shape: the whole pre-fix Qodana topology, with the private consumer of each copy.
   *
   * `intellij.qodana.sarif`, `intellij.dfa.analysis.rml.utils` and `intellij.ml.llm.qodana.agents` each declared
   * `<module name="intellij.libraries.qodana.sarif"/>`. Each of these modules resolves that name inside its own
   * plugin. The rule must report a module only for the copies that the module can reach.
   */
  @Test
  fun `reports each seeing module with the owners that it reaches`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.qodana")
        bundlesPlugin("intellij.dfa.analysis.plugin")
        bundlesPlugin("intellij.ml.llm")
        bundlesPlugin("intellij.jvm.dfa.analysis")
      }
      plugin("intellij.qodana") {
        pluginId("org.intellij.qodana")
        embeddedCopy("intellij.libraries.qodana.sarif")
        content("intellij.qodana.sarif")
      }
      plugin("intellij.dfa.analysis.plugin") {
        pluginId("com.intellij.dfa.analysis")
        embeddedCopy("intellij.libraries.qodana.sarif")
        content("intellij.dfa.analysis.rml.utils")
      }
      plugin("intellij.ml.llm") {
        pluginId("com.intellij.ml.llm")
        embeddedCopy("intellij.libraries.qodana.sarif")
        content("intellij.ml.llm.qodana.agents")
      }
      plugin("intellij.jvm.dfa.analysis") {
        pluginId("com.intellij.jvm.dfa.analysis")
        content("intellij.jvm.dfa.analysis.inspections")
      }
      // every plugin has a content module that names its own private copy
      linkContentModuleDeps("intellij.qodana.sarif", "intellij.libraries.qodana.sarif")
      linkContentModuleDeps("intellij.dfa.analysis.rml.utils", "intellij.libraries.qodana.sarif")
      linkContentModuleDeps("intellij.ml.llm.qodana.agents", "intellij.libraries.qodana.sarif", "intellij.qodana.sarif")
      linkContentModuleDeps("intellij.jvm.dfa.analysis.inspections", "intellij.qodana.sarif")
    }

    val errors = runCopyConflictRule(
      graph,
      listOf(
        descriptorPluginDependencies("intellij.jvm.dfa.analysis.inspections", "com.intellij.dfa.analysis"),
        descriptorPluginDependencies("intellij.ml.llm.qodana.agents", "org.intellij.qodana"),
      ),
    )

    val conflicts = singleErrorConflicts(errors)
    // the conflict list is sorted by duplicated name, then by seeing module
    assertThat(conflicts.map { it.module.value })
      .containsExactly("intellij.jvm.dfa.analysis.inspections", "intellij.ml.llm.qodana.agents")

    assertThat(conflicts[0].owners.map { it.plugin.value })
      .containsExactly("intellij.dfa.analysis.plugin", "intellij.qodana")
    assertThat(conflicts[1].owners.map { it.plugin.value })
      .containsExactly("intellij.ml.llm", "intellij.qodana")
    assertThat(conflicts[1].owners[0].path).containsExactly(
      "intellij.ml.llm.qodana.agents",
      "is a content module of plugin intellij.ml.llm",
    )
    // the plugin dependency of the descriptor is the first hop that the walk finds
    assertThat(conflicts[1].owners[1].path).containsExactly(
      "intellij.ml.llm.qodana.agents",
      "declares <plugin id=\"org.intellij.qodana\">",
    )
  }

  /**
   * Real shape: two plugins that each keep a private copy of one library module.
   *
   * Neither plugin reaches the other. This is the case that the plugin model supports. A private copy per
   * plugin is legal, and each copy has a content module that names it.
   * The rule must stay silent, because no module can load both copies.
   */
  @Test
  fun `does not report two isolated private copies`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.a")
        bundlesPlugin("intellij.plugin.b")
      }
      plugin("intellij.plugin.a") {
        pluginId("com.example.a")
        embeddedCopy("intellij.libraries.shared")
        content("intellij.plugin.a.impl")
      }
      plugin("intellij.plugin.b") {
        pluginId("com.example.b")
        embeddedCopy("intellij.libraries.shared")
        content("intellij.plugin.b.impl")
      }
      linkContentModuleDeps("intellij.plugin.a.impl", "intellij.libraries.shared")
      linkContentModuleDeps("intellij.plugin.b.impl", "intellij.libraries.shared")
    }

    val errors = runCopyConflictRule(graph)

    assertThat(errors).isEmpty()
  }

  /**
   * Real shape: two plugins that each keep a private copy of the same two library modules.
   *
   * One `<content>` block often holds several private library copies. See the block in
   * `contrib/qodana/core/resources/META-INF/plugin.xml`. So one plugin pair can share more than one name.
   *
   * This case pins the generalization: the expansion test must hold for every private name of the owning plugin,
   * and not only for the name that the analysis handles at the time. With the narrow rule the walk from plugin a
   * stops at `intellij.libraries.lib.one` but expands `intellij.libraries.lib.two`, so it reaches the consumer of
   * plugin b and the false report comes back under the second name.
   *
   * The reverse walk cache is a second reason for the same rule. Under the narrow rule the reach of a plugin
   * changes with the name in hand, so a cache that holds one reach per plugin gives the second name the reach of
   * the first. The expansion test must not depend on the name in hand, to keep that cache correct.
   */
  @Test
  fun `does not report isolated private copies when two names are duplicated`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.a")
        bundlesPlugin("intellij.plugin.b")
      }
      plugin("intellij.plugin.a") {
        pluginId("com.example.a")
        embeddedCopy("intellij.libraries.lib.one")
        embeddedCopy("intellij.libraries.lib.two")
        content("intellij.plugin.a.impl")
      }
      plugin("intellij.plugin.b") {
        pluginId("com.example.b")
        embeddedCopy("intellij.libraries.lib.one")
        embeddedCopy("intellij.libraries.lib.two")
        content("intellij.plugin.b.impl")
      }
      // each plugin names one of the two copies, and each name resolves inside the plugin of the consumer
      linkContentModuleDeps("intellij.plugin.a.impl", "intellij.libraries.lib.one")
      linkContentModuleDeps("intellij.plugin.b.impl", "intellij.libraries.lib.two")
    }

    val errors = runCopyConflictRule(graph)

    assertThat(errors).isEmpty()
  }

  /**
   * Real shape: the six names of the first generator run, each declared non-embedded by two plugins.
   *
   * The rule counts only an embedded copy. A non-embedded module keeps its own classloader and one runtime ID
   * per name, so two plugins that declare the name share one module instead of holding a copy each.
   *
   * The graph makes the case as hard as it can be: one module names the module and reaches both plugins. The
   * rule must still stay silent. Before the embedded-only change the walk reported such a module and gave both
   * owner rows the same path, because a module-rooted walk must expand the name node and that step crosses
   * owners.
   */
  @Test
  fun `does not report two non-embedded copies that one module reaches`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.a")
        bundlesPlugin("intellij.plugin.b")
        bundlesPlugin("intellij.consumer.plugin")
      }
      plugin("intellij.plugin.a") {
        pluginId("com.example.a")
        content("intellij.libraries.shared", namespace = null, loading = ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("intellij.plugin.b") {
        pluginId("com.example.b")
        content("intellij.libraries.shared", namespace = null, loading = ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("intellij.consumer.plugin") {
        pluginId("com.example.consumer")
        content("intellij.consumer.impl")
        dependsOnPlugin("com.example.a")
        dependsOnPlugin("com.example.b")
      }
      linkContentModuleDeps("intellij.consumer.impl", "intellij.libraries.shared")
    }

    val errors = runCopyConflictRule(graph)

    assertThat(errors).isEmpty()
  }

  /**
   * Real shape: one plugin keeps the library private and embedded, and a second plugin ships the same name as a
   * non-embedded module.
   *
   * A candidate name needs an embedded declaration from two or more plugins. Only one plugin embeds here, so the
   * name is no candidate and the reach of the module does not matter.
   */
  @Test
  fun `does not report an embedded copy beside a non-embedded copy`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.a")
        bundlesPlugin("intellij.plugin.b")
        bundlesPlugin("intellij.consumer.plugin")
      }
      plugin("intellij.plugin.a") {
        pluginId("com.example.a")
        embeddedCopy("intellij.libraries.shared")
      }
      plugin("intellij.plugin.b") {
        pluginId("com.example.b")
        content("intellij.libraries.shared", namespace = null, loading = ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("intellij.consumer.plugin") {
        pluginId("com.example.consumer")
        content("intellij.consumer.impl")
        dependsOnPlugin("com.example.a")
        dependsOnPlugin("com.example.b")
      }
      linkContentModuleDeps("intellij.consumer.impl", "intellij.libraries.shared")
    }

    val errors = runCopyConflictRule(graph)

    assertThat(errors).isEmpty()
  }

  /**
   * Real shape: `intellij.libraries.completion.ranking.js`, which one plugin declares `loading="embedded"` and
   * another declares `loading="required"`.
   *
   * Two questions look alike, so the analysis keeps two answers.
   *
   * A name is a CANDIDATE, so reportable, when two or more bundled plugins declare an embedded copy of it.
   * Whether the walk may expand a name is a question about the walk and not about the name, so `canExpand` holds
   * it rather than a set. A walk that arrives at a name through `containsContent` of plugin Y stands on Y's copy,
   * and it expands only when Y declared the name with a namespace. A walk that arrives through a dependency
   * expands only when some bundled plugin declares the name with a namespace.
   *
   * The seer guard uses `nonSeerNameNodes`, which is the union of the candidate names and the AMBIGUOUS names.
   * A name is ambiguous when two or more bundled plugins declare it, whatever the loading rule and whatever the
   * namespace. Privacy plays no part in that test. A candidate needs two distinct embedded declarers, so it has
   * two declarers, so every candidate is ambiguous and the union equals the ambiguous set. The code keeps the
   * union anyway, so that a narrower candidate rule can never widen the set.
   *
   * `intellij.libraries.mid` below is private in both plugins and is no candidate, because only one of the two
   * declarations is embedded. It is still ambiguous, so it is no seer, and no walk may expand it.
   *
   * The lesson to keep: a narrower rule is not a quieter rule. The embedded-only change SHRANK the candidate set.
   * While one set answered both questions, that dropped names out of the expansion test and produced a NEW report.
   */
  @Test
  fun `does not expand an ambiguous name that is not a candidate`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.a")
        bundlesPlugin("intellij.plugin.b")
        bundlesPlugin("intellij.consumer.plugin")
      }
      plugin("intellij.plugin.a") {
        pluginId("com.example.a")
        embeddedCopy("intellij.libraries.mid")
        embeddedCopy("intellij.libraries.leaf")
      }
      plugin("intellij.plugin.b") {
        pluginId("com.example.b")
        // the same name and the same private namespace, and a loading rule that keeps it out of the candidate set
        content("intellij.libraries.mid", namespace = null, loading = ModuleLoadingRuleValue.REQUIRED)
        embeddedCopy("intellij.libraries.leaf")
      }
      plugin("intellij.consumer.plugin") {
        pluginId("com.example.consumer")
        content("intellij.consumer.impl")
      }
      // the only route from the consumer to either copy of the leaf name runs through the ambiguous name
      linkContentModuleDeps("intellij.consumer.impl", "intellij.libraries.mid")
    }

    val errors = runCopyConflictRule(graph)

    assertThat(errors).isEmpty()
  }

  /**
   * Real shape: a descriptor that declares `<plugin id="..."/>` on a plugin that embeds a private library copy.
   *
   * `intellij.consumer.mid` sees both copies, because it depends on both owning plugins. That report is right.
   * `intellij.consumer.leafUser` sees neither. It depends on the NAME `intellij.consumer.mid`, and only plugin r
   * declares that name, privately. So no reference from outside plugin r can resolve it, and the walk must not
   * step from that name to the modules that name it.
   *
   * The walk arrives at `intellij.consumer.mid` through a plugin dependency, not through the content of a plugin.
   * The expansion test must therefore ask whether any plugin declares the name with a namespace. It must not ask
   * whether the plugin it came from declares the name privately, because that plugin never declares the name.
   */
  @Test
  fun `does not step from a private name that a plugin dependency reached`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.p")
        bundlesPlugin("intellij.plugin.q")
        bundlesPlugin("intellij.plugin.r")
        bundlesPlugin("intellij.plugin.s")
      }
      plugin("intellij.plugin.p") {
        pluginId("com.example.p")
        embeddedCopy("intellij.libraries.leaf")
      }
      plugin("intellij.plugin.q") {
        pluginId("com.example.q")
        embeddedCopy("intellij.libraries.leaf")
      }
      plugin("intellij.plugin.r") {
        pluginId("com.example.r")
        // no namespace, so no reference from outside plugin r resolves this name
        content("intellij.consumer.mid", namespace = null)
      }
      plugin("intellij.plugin.s") {
        pluginId("com.example.s")
        content("intellij.consumer.leafUser")
      }
      linkContentModuleDeps("intellij.consumer.leafUser", "intellij.consumer.mid")
    }

    val errors = runCopyConflictRule(
      graph,
      listOf(descriptorPluginDependencies("intellij.consumer.mid", "com.example.p", "com.example.q")),
    )

    val conflicts = singleErrorConflicts(errors)
    assertThat(conflicts.map { it.module.value }).containsExactly("intellij.consumer.mid")
    assertThat(conflicts.single().owners.map { it.plugin.value })
      .containsExactly("intellij.plugin.p", "intellij.plugin.q")
  }

  /**
   * Real shape: a descriptor that names a plugin by an alias, and the alias node carries the dependency.
   *
   * An alias node holds no content of its own. So a test that asks whether the node the walk came from declares
   * the name privately can never say no here, and the walk expands every name that an alias dependency reaches.
   * The arrival is a plugin dependency, so the same rule as the case above applies to it.
   */
  @Test
  fun `does not step from a private name that an alias dependency reached`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.p")
        bundlesPlugin("intellij.plugin.q")
        bundlesPlugin("intellij.plugin.r")
        bundlesPlugin("intellij.plugin.s")
      }
      plugin("intellij.plugin.p") {
        pluginId("com.example.p")
        embeddedCopy("intellij.libraries.leaf")
      }
      plugin("intellij.plugin.q") {
        pluginId("com.example.q")
        embeddedCopy("intellij.libraries.leaf")
      }
      plugin("intellij.plugin.r") {
        pluginId("com.example.r")
        content("intellij.consumer.mid", namespace = null)
      }
      plugin("intellij.plugin.s") {
        pluginId("com.example.s")
        content("intellij.consumer.leafUser")
      }
      linkContentModuleDeps("intellij.consumer.leafUser", "intellij.consumer.mid")
      // the descriptor below names these aliases, so every arrival at the name comes through an alias node
      pluginAlias(productName = "IDEA", pluginName = "intellij.plugin.p", alias = "com.example.p.alias")
      pluginAlias(productName = "IDEA", pluginName = "intellij.plugin.q", alias = "com.example.q.alias")
    }

    val errors = runCopyConflictRule(
      graph,
      listOf(descriptorPluginDependencies("intellij.consumer.mid", "com.example.p.alias", "com.example.q.alias")),
    )

    val conflicts = singleErrorConflicts(errors)
    assertThat(conflicts.map { it.module.value }).containsExactly("intellij.consumer.mid")
    assertThat(conflicts.single().owners.map { it.plugin.value })
      .containsExactly("intellij.plugin.p", "intellij.plugin.q")
  }

  /**
   * Real shape: a descriptor names a plugin by an alias, such as `com.intellij.modules.java`.
   *
   * The graph keeps an alias ID on its own node, so a dependency on the alias does not point at the plugin that
   * declares it. The walk takes the alias edge as an extra hop, which makes the owner behind the alias visible.
   */
  @Test
  fun `counts an owner that a content module reaches through an alias`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.java.plugin")
        bundlesPlugin("intellij.other.plugin")
        bundlesPlugin("intellij.consumer.plugin")
      }
      plugin("intellij.java.plugin") {
        pluginId("com.intellij.java")
        embeddedCopy("intellij.libraries.shared")
      }
      plugin("intellij.other.plugin") {
        pluginId("com.intellij.other")
        embeddedCopy("intellij.libraries.shared")
      }
      plugin("intellij.consumer.plugin") {
        pluginId("com.intellij.consumer")
        content("intellij.consumer.impl")
        dependsOnPlugin("com.intellij.other")
      }
      pluginAlias(productName = "IDEA", pluginName = "intellij.java.plugin", alias = "com.intellij.modules.java")
    }

    val errors = runCopyConflictRule(
      graph,
      listOf(descriptorPluginDependencies("intellij.consumer.impl", "com.intellij.modules.java")),
    )

    val conflict = singleErrorConflicts(errors).single()
    assertThat(conflict.module).isEqualTo(ContentModuleName("intellij.consumer.impl"))
    assertThat(conflict.owners.map { it.plugin.value })
      .containsExactly("intellij.java.plugin", "intellij.other.plugin")
    // the alias hop makes the last step of the path
    assertThat(conflict.owners[0].path).containsExactly(
      "intellij.consumer.impl",
      "declares <plugin id=\"com.intellij.modules.java\">",
      "is an alias of plugin intellij.java.plugin",
    )
    assertThat(conflict.owners[1].path).containsExactly(
      "intellij.consumer.impl",
      "is a content module of plugin intellij.consumer.plugin",
      "declares <plugin id=\"com.intellij.other\">",
    )
  }

  /**
   * Real shape: the seeded names of `suppressions.json` beside a name that nobody listed.
   *
   * Eight names are grandfathered under `contentModuleCopyConflicts`. A listed name must stay silent, and a name
   * that nobody listed must still fail the build.
   *
   * Two mechanisms silence a name, and this case covers the first one. The node reads
   * `contentModuleCopyConflicts` itself and drops a listed name, so one graph shows both halves. The error also
   * carries a `suppressionKey` for the filter of the pipeline, which the next case covers.
   * One error per duplicated name is what lets either mechanism name one module.
   */
  @Test
  fun `suppresses a seeded name and keeps an unseeded name`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.a")
        bundlesPlugin("intellij.plugin.b")
        bundlesPlugin("intellij.consumer.plugin")
      }
      plugin("intellij.plugin.a") {
        pluginId("com.example.a")
        embeddedCopy("intellij.libraries.lib.one")
        embeddedCopy("intellij.libraries.lib.two")
      }
      plugin("intellij.plugin.b") {
        pluginId("com.example.b")
        embeddedCopy("intellij.libraries.lib.one")
        embeddedCopy("intellij.libraries.lib.two")
      }
      plugin("intellij.consumer.plugin") {
        pluginId("com.example.consumer")
        content("intellij.consumer.impl")
        dependsOnPlugin("com.example.a")
        dependsOnPlugin("com.example.b")
      }
    }

    val errors = runCopyConflictRule(graph)

    // one error per duplicated name, sorted by name, each with the key that names that one module
    assertThat(errors.map { (it as ContentModuleCopyConflictError).duplicatedModule.value })
      .containsExactly("intellij.libraries.lib.one", "intellij.libraries.lib.two")
    assertThat(errors.map { it.suppressionKey }).containsExactly(
      ContentModuleCopyConflictError.suppressionKeyFor(ContentModuleName("intellij.libraries.lib.one")),
      ContentModuleCopyConflictError.suppressionKeyFor(ContentModuleName("intellij.libraries.lib.two")),
    )

    // the node drops a listed name and keeps the name that the config does not list
    val config = SuppressionConfig(
      contentModuleCopyConflicts = mapOf(
        ContentModuleName("intellij.libraries.lib.one") to
          ContentModuleCopyConflictException(reason = "DEBT: Owners: intellij.plugin.a, intellij.plugin.b."),
      ),
    )
    val remaining = runCopyConflictRule(graph, suppressionConfig = config)

    assertThat(remaining.map { (it as ContentModuleCopyConflictError).duplicatedModule.value })
      .containsExactly("intellij.libraries.lib.two")
  }

  /**
   * The suppression filter of the pipeline, tested on its own.
   *
   * `Pipeline.aggregate` drops an error whose `suppressionKey` the config suppresses. That step sits outside the
   * node, so no validator test can see it. The predicate itself is a pure function, so it needs no pipeline.
   *
   * The two spellings must agree. The key of the error uses a camelCase prefix, and `isSuppressed` splits the key
   * on the same prefix. A rename on one side alone makes the prefix branch dead, and then the map is never read.
   * This case fails on that rename, because it builds the key with the companion function of the error.
   */
  @Test
  fun `the config suppresses a seeded name only`() {
    val seeded = ContentModuleName("intellij.libraries.lib.one")
    val unseeded = ContentModuleName("intellij.libraries.lib.two")
    val config = SuppressionConfig(
      contentModuleCopyConflicts = mapOf(
        seeded to ContentModuleCopyConflictException(reason = "DEBT: Owners: intellij.plugin.a, intellij.plugin.b."),
      ),
    )

    assertThat(config.isSuppressed(ContentModuleCopyConflictError.suppressionKeyFor(seeded))).isTrue()
    assertThat(config.isSuppressed(ContentModuleCopyConflictError.suppressionKeyFor(unseeded))).isFalse()
    // an empty config suppresses nothing, so a new name always fails
    assertThat(SuppressionConfig().isSuppressed(ContentModuleCopyConflictError.suppressionKeyFor(seeded))).isFalse()
  }

  /**
   * Real shape: one plugin embeds a private library copy, and many modules reach that plugin.
   *
   * One owner loads one class set, so no module can get the same class twice. The rule needs two owners of the
   * name before it looks at a path.
   */
  @Test
  fun `does not report a single owner`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("intellij.plugin.a")
        bundlesPlugin("intellij.consumer.plugin")
      }
      plugin("intellij.plugin.a") {
        pluginId("com.example.a")
        embeddedCopy("intellij.libraries.shared")
        content("intellij.plugin.a.impl")
        content("intellij.plugin.a.ui")
      }
      plugin("intellij.consumer.plugin") {
        pluginId("com.example.consumer")
        content("intellij.consumer.impl")
        dependsOnPlugin("com.example.a")
      }
      linkContentModuleDeps("intellij.plugin.a.ui", "intellij.plugin.a.impl")
      linkContentModuleDeps("intellij.consumer.impl", "intellij.plugin.a.impl")
    }

    val errors = runCopyConflictRule(graph)

    assertThat(errors).isEmpty()
  }
}

/**
 * Add a private embedded copy of a library content module.
 *
 * A `<content>` block without a namespace gives the copy an implicit per-plugin namespace, and `embedded` puts
 * the copy in the main classloader of the plugin. This pair is the shape of the QD-15883 defect.
 */
private fun GraphPluginBuilder.embeddedCopy(module: String) {
  content(module, namespace = null, loading = ModuleLoadingRuleValue.EMBEDDED)
}

/**
 * The `<plugin id="..."/>` dependencies of one content module descriptor.
 *
 * The graph holds no content module to plugin edge, so the rule reads these from the content module plan.
 * Only [ContentModuleDependencyPlan.writtenPluginDependencies] is used, and the other fields stay empty.
 */
private fun descriptorPluginDependencies(module: String, vararg pluginIds: String): ContentModuleDependencyPlan {
  val dependencies = pluginIds.map(::PluginId)
  return ContentModuleDependencyPlan(
    contentModuleName = ContentModuleName(module),
    descriptorPath = Path.of("$module.xml"),
    descriptorContent = "",
    moduleDependencies = emptyList(),
    pluginDependencies = dependencies,
    testDependencies = emptyList(),
    existingXmlModuleDependencies = emptySet(),
    existingXmlPluginDependencies = emptySet(),
    preserveExistingPluginDependencies = emptySet(),
    writtenPluginDependencies = dependencies,
    requiredPluginDependencies = emptySet(),
    suppressedModules = emptySet(),
    suppressedPlugins = emptySet(),
    suppressionUsages = emptyList(),
  )
}

/**
 * Runs the rule over one graph.
 *
 * [suppressionConfig] reaches the node through the model, because the node reads
 * `contentModuleCopyConflicts` itself.
 */
private suspend fun runCopyConflictRule(
  graph: PluginGraph,
  plans: List<ContentModuleDependencyPlan> = emptyList(),
  suppressionConfig: SuppressionConfig = SuppressionConfig(),
): List<ValidationError> {
  return runValidationRule(
    ContentModuleCopyConflictValidator,
    testGenerationModel(graph, suppressionConfig = suppressionConfig),
    slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = plans)),
  )
}

/** The conflicts of the one product of the graph. */
private fun singleErrorConflicts(errors: List<ValidationError>) =
  (errors.single() as ContentModuleCopyConflictError).conflicts
