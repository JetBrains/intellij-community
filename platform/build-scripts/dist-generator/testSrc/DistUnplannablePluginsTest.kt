// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.distGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginDistribution
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.intellij.build.impl.DescriptorMarker
import org.jetbrains.intellij.build.impl.DescriptorMarkerPatcher
import org.jetbrains.intellij.build.impl.JpsModuleOutputProviderState
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginVersionEvaluator
import org.jetbrains.intellij.build.impl.PluginVersionEvaluatorResult
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.SuffixedPluginVersion
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The rejection sites of the distribution plan over synthetic layouts: each stops the run, for a bundled and an
 * additional plugin alike. The layout bindings drop a rejected registry layout and keep the run. Also the divergence
 * rule: two products that plan one plugin share one home or give the later product a product home.
 */
class DistUnplannablePluginsTest {
  @TempDir
  lateinit var dir: Path

  private val versionAsCode = PluginVersionEvaluator { _, context ->
    PluginVersionEvaluatorResult(pluginVersion = "${context.pluginBuildNumber}-code")
  }

  private val linuxX64 = PluginSymbolicVariant(id = "linux_x64", distribution = SUPPORTED_DISTRIBUTIONS.single { it.os == OsFamily.LINUX && it.arch == JvmArchitecture.x64 })
  private val macAarch64 = PluginSymbolicVariant(id = "mac_aarch64", distribution = SUPPORTED_DISTRIBUTIONS.single { it.os == OsFamily.MACOS && it.arch == JvmArchitecture.aarch64 })

  private fun layoutWithVersionAsCode(): PluginLayout = PluginLayout.pluginWithCustomLayout("intellij.x") { it.withCustomVersion(versionAsCode) }

  /** A layout with a resource of a module that the Bazel target index does not place, so no binding can state it. */
  private fun layoutWithUnplacedResource(mainModule: String = "intellij.unplaced"): PluginLayout {
    return PluginLayout.pluginWithCustomLayout(mainModule) { it.withResource("missing", "missing") }
  }

  private fun plainLayout(mainModule: String): PluginLayout = PluginLayout.pluginWithCustomLayout(mainModule) { }

  /** A layout that excludes a directory from its module jar. The distribution has no module filter, so no binding states it. */
  private fun layoutWithModuleExcludes(mainModule: String = "intellij.filtered"): PluginLayout {
    return PluginLayout.pluginWithCustomLayout(mainModule) { it.excludeFromModule(mainModule, "server/**") }
  }

  private fun request(layout: PluginLayout, tier: DistPluginTier, variant: PluginSymbolicVariant = linuxX64): DistPluginRequest {
    return DistPluginRequest(product = "idea", properties = SyntheticProductProperties(), tier = tier, variant = variant, layout = layout)
  }

  private fun emptyIndex(): DistBazelIndex {
    val targets = BazelTargetsInfo.TargetsFile(modules = emptyMap(), projectLibraries = emptyMap(), pluginDistributionTargets = emptyMap())
    return DistBazelIndex(targets = targets, projectRoot = dir)
  }

  private fun emptyOutputProvider(): ModuleOutputProvider {
    return JpsModuleOutputProviderState(JpsElementFactory.getInstance().createModel().project).createProvider(useTestCompilationOutput = false)
  }

  private fun rejection(layout: PluginLayout, variant: String, os: OsFamily?, arch: JvmArchitecture?): DistUnplannableLayoutException {
    return assertThrows<DistUnplannableLayoutException> { statedDescriptorFacts(layout.mainModule, variant, layout, os, arch) }
  }

  @Test
  fun `a layout with versionEvaluator as code fails on every variant`() {
    val layout = layoutWithVersionAsCode()
    val variants = listOf(
      Triple("", null, null),
      Triple("windows_x64", OsFamily.WINDOWS, JvmArchitecture.x64),
      Triple("linux_aarch64", OsFamily.LINUX, JvmArchitecture.aarch64),
    )

    for ((variant, os, arch) in variants) {
      assertThat(rejection(layout, variant, os, arch)).hasMessage(
        "Cannot plan the descriptor for plugin 'intellij.x' variant '$variant':" +
        " the layout states versionEvaluator as code, so no suffix states it"
      )
    }
  }

  @Test
  fun `a version suffix as data is stated`() {
    val layout = PluginLayout.pluginWithCustomLayout("intellij.x") { it.withCustomVersion(SuffixedPluginVersion("-suffix")) }

    val facts = statedDescriptorFacts(layout.mainModule, "", layout, null, null)

    assertThat(facts.versionSuffix).isEqualTo("-suffix")
    assertThat(facts.markers).isEmpty()
    assertThat(facts.derivesOsArchStamps).isFalse()
  }

  @Test
  fun `the layout bindings fail for a plugin with an unplaced resource in either tier`() {
    for (tier in DIST_COMPONENT_TIERS) {
      val requests = listOf(request(plainLayout("intellij.plain"), tier), request(layoutWithUnplacedResource(), tier))

      assertThatThrownBy { bindGeneratedDistPluginLayouts(requests, emptyIndex(), emptyOutputProvider(), CommunityDistHalf) }
        .describedAs(tier.name)
        .isInstanceOf(DistUnplannableLayoutException::class.java)
        .hasMessage("Plugin 'intellij.unplaced' declares the resource 'missing' of module 'intellij.unplaced', and the Bazel target index does not place that module")
    }
  }

  @Test
  fun `the layout bindings fail for a plugin that excludes a directory from a module jar`() {
    for (tier in DIST_COMPONENT_TIERS) {
      val requests = listOf(request(plainLayout("intellij.plain"), tier), request(layoutWithModuleExcludes(), tier))

      assertThatThrownBy { bindGeneratedDistPluginLayouts(requests, emptyIndex(), emptyOutputProvider(), CommunityDistHalf) }
        .describedAs(tier.name)
        .isInstanceOf(DistUnplannableLayoutException::class.java)
        .hasMessage(
          "Plugin 'intellij.filtered' excludes [server/**] from the module 'intellij.filtered'. " +
          "The distribution has no module filter: move the directory out of the resource root and copy it with withResource."
        )
    }
  }

  @Test
  fun `the layout bindings name the first rejected request in request order`() {
    val requests = listOf("intellij.b", "intellij.a", "intellij.c").map { request(layoutWithUnplacedResource(it), DistPluginTier.ADDITIONAL) }

    assertThatThrownBy { bindGeneratedDistPluginLayouts(requests, emptyIndex(), emptyOutputProvider(), CommunityDistHalf) }
      .isInstanceOf(DistUnplannableLayoutException::class.java)
      .hasMessageStartingWith("Plugin 'intellij.b' declares")
  }

  @Test
  fun `a run-configuration module without a layout or a platform variant fails like a hand extra without a layout`() {
    val crossPlatformOnly = PluginLayout.pluginWithCustomLayout("intellij.cross") {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }
    val macOnly = PluginLayout.pluginWithCustomLayout("intellij.mac") {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    }
    val properties = SyntheticProductProperties(persistentListOf(crossPlatformOnly, macOnly, plainLayout("intellij.plain")))
    fun requests(additionalModules: List<String> = emptyList(), extraPluginModules: List<String> = emptyList()): List<DistPluginRequest> {
      return enumerateDistPluginRequests(
        product = "idea",
        properties = properties,
        bundledPluginModules = emptyList(),
        variants = listOf(linuxX64),
        additionalModules = additionalModules,
        extraPluginModules = extraPluginModules,
      )
    }

    assertThat(requests(additionalModules = listOf("intellij.plain")).map { it.layout.mainModule to it.tier })
      .containsExactly("intellij.plain" to DistPluginTier.ADDITIONAL)
    assertThatThrownBy { requests(additionalModules = listOf("intellij.cross", "intellij.plain")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Additional plugin modules of 'idea' have no plugin layout: [intellij.cross]")
    assertThatThrownBy { requests(extraPluginModules = listOf("intellij.cross")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Additional plugin modules of 'idea' have no plugin layout: [intellij.cross]")
    assertThatThrownBy { requests(additionalModules = listOf("intellij.mac", "intellij.plain")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("No platform variant selects the additional plugin modules of 'idea': [intellij.mac]")
  }

  private fun registryRequests(
    properties: ProductProperties,
    registryModules: List<String>,
    variants: List<PluginSymbolicVariant> = listOf(linuxX64),
    bundledPluginModules: List<String> = emptyList(),
    additionalModules: List<String> = emptyList(),
  ): List<DistPluginRequest> {
    return enumerateDistPluginRequests(
      product = "idea",
      properties = properties,
      bundledPluginModules = bundledPluginModules,
      variants = variants,
      additionalModules = additionalModules,
      extraPluginModules = emptyList(),
      registryModules = registryModules,
    )
  }

  @Test
  fun `a registry layout yields requests of the registry tier`() {
    val properties = SyntheticProductProperties(persistentListOf(plainLayout("intellij.bundled"), plainLayout("intellij.registry")))

    val requests = registryRequests(properties, registryModules = listOf("intellij.registry"), variants = listOf(linuxX64, macAarch64), bundledPluginModules = listOf("intellij.bundled"))

    assertThat(requests.map { Triple(it.layout.mainModule, it.variant.id, it.tier) }).containsExactly(
      Triple("intellij.bundled", "linux_x64", DistPluginTier.BUNDLED),
      Triple("intellij.registry", "linux_x64", DistPluginTier.REGISTRY),
      Triple("intellij.bundled", "mac_aarch64", DistPluginTier.BUNDLED),
      Triple("intellij.registry", "mac_aarch64", DistPluginTier.REGISTRY),
    )
  }

  @Test
  fun `a platform-restricted registry layout yields no request on another platform and does not fail`() {
    val macOnly = PluginLayout.pluginAuto("intellij.mac") {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    }
    val properties = SyntheticProductProperties(persistentListOf(macOnly))

    // An additional module of this layout stops the run, see the run-configuration test.
    assertThat(registryRequests(properties, registryModules = listOf("intellij.mac"))).isEmpty()
    assertThat(registryRequests(properties, registryModules = listOf("intellij.mac"), variants = listOf(linuxX64, macAarch64)).map { it.variant.id to it.tier })
      .containsExactly("mac_aarch64" to DistPluginTier.REGISTRY)
  }

  @Test
  fun `a marketplace-only registry layout yields no request`() {
    val marketplaceOnly = PluginLayout.pluginAuto("intellij.marketplace") {
      it.bundlingRestrictions.marketplace = true
    }
    val properties = SyntheticProductProperties(persistentListOf(marketplaceOnly))

    assertThat(registryRequests(properties, registryModules = listOf("intellij.marketplace"), variants = listOf(linuxX64, macAarch64))).isEmpty()
  }

  @Test
  fun `a marketplace-only update module gets an additional request, and of a pair the marketplace layout`() {
    val marketplaceOnly = PluginLayout.pluginAuto("intellij.marketplace") { it.bundlingRestrictions.marketplace = true }
    val bundledVariant = PluginLayout.pluginAuto("intellij.pair") { it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.LINUX) }
    val marketplaceVariant = PluginLayout.pluginAuto("intellij.pair") { it.bundlingRestrictions.marketplace = true }
    val properties = SyntheticProductProperties(persistentListOf(marketplaceOnly, bundledVariant, marketplaceVariant))

    val requests = enumerateDistPluginRequests(
      product = "idea",
      properties = properties,
      bundledPluginModules = emptyList(),
      variants = listOf(linuxX64, macAarch64),
      additionalModules = emptyList(),
      extraPluginModules = emptyList(),
      updateModules = listOf("intellij.marketplace", "intellij.pair"),
    )

    assertThat(requests.map { listOf(it.layout.mainModule, it.variant.id, it.tier, it.updateOnly) }).containsExactly(
      listOf("intellij.marketplace", "linux_x64", DistPluginTier.ADDITIONAL, true),
      listOf("intellij.pair", "linux_x64", DistPluginTier.ADDITIONAL, true),
      listOf("intellij.marketplace", "mac_aarch64", DistPluginTier.ADDITIONAL, true),
      listOf("intellij.pair", "mac_aarch64", DistPluginTier.ADDITIONAL, true),
    )
    assertThat(requests.filter { it.layout.mainModule == "intellij.pair" }.map { it.layout }).allMatch { it === marketplaceVariant }
    // The registry tier keeps the bundled rule, see `a marketplace-only registry layout yields no request`.
    assertThat(registryRequests(properties, registryModules = listOf("intellij.marketplace"))).isEmpty()
  }

  @Test
  fun `several refused update modules are named in one failure, and a hold-out is not`() {
    val crossPlatformOnly = PluginLayout.pluginWithCustomLayout("intellij.cross") {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }
    val macOnly = PluginLayout.pluginWithCustomLayout("intellij.mac") {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    }
    val properties = SyntheticProductProperties(persistentListOf(crossPlatformOnly, macOnly, plainLayout("intellij.plain")))
    val updateModules = listOf("intellij.cross", "intellij.mac", "intellij.plain")
    val refusals = DistUpdateRefusals()

    val requests = enumerateDistPluginRequests(
      product = "idea",
      properties = properties,
      bundledPluginModules = emptyList(),
      variants = listOf(linuxX64),
      additionalModules = emptyList(),
      extraPluginModules = emptyList(),
      updateModules = updateModules,
      refuseUpdate = { mainModule, reason -> refusals.add("idea", mainModule, DistUpdateRefusalStage.ENUMERATION, reason) },
    )

    assertThat(requests.map { it.layout.mainModule }).containsExactly("intellij.plain")
    assertThat(refusals.refused("idea")).containsExactly("intellij.cross", "intellij.mac")
    assertThatThrownBy { refusals.check(holdOuts = mapOf("idea" to emptyMap()), updatePluginModules = mapOf("idea" to updateModules)) }
      .isInstanceOf(DistUnplannableLayoutException::class.java)
      .hasMessage(
        "The plan cannot state these update plugins (2). Fix each one, or hold it out with a reason in `updatePluginHoldOuts` of its split product:\n" +
        "  idea intellij.cross (request enumeration): The update plugin 'intellij.cross' of 'idea' has no plugin layout\n" +
        "  idea intellij.mac (request enumeration): No platform variant selects the update plugin 'intellij.mac' of 'idea'"
      )
    // Two hold-outs cover the refusals. A hold-out that the plan states, or that is no update plugin, is stale.
    refusals.check(
      holdOuts = mapOf("idea" to mapOf("intellij.cross" to "no layout", "intellij.mac" to "macOS only")),
      updatePluginModules = mapOf("idea" to updateModules),
    )
    assertThatThrownBy {
      refusals.check(
        holdOuts = mapOf("idea" to mapOf("intellij.cross" to "a", "intellij.mac" to "b", "intellij.plain" to "c", "intellij.gone" to "d")),
        updatePluginModules = mapOf("idea" to updateModules),
      )
    }
      .isInstanceOf(DistUnplannableLayoutException::class.java)
      .hasMessage(
        "Remove these stale hold-outs (2) from `updatePluginHoldOuts`:\n" +
        "  idea intellij.gone: it is no update plugin of the product\n" +
        "  idea intellij.plain: the plan states it"
      )
  }

  @Test
  fun `a refused update module that a run configuration names stops the run`() {
    val crossPlatformOnly = PluginLayout.pluginWithCustomLayout("intellij.cross") {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }
    val properties = SyntheticProductProperties(persistentListOf(crossPlatformOnly))

    assertThatThrownBy {
      enumerateDistPluginRequests(
        product = "idea",
        properties = properties,
        bundledPluginModules = emptyList(),
        variants = listOf(linuxX64),
        additionalModules = listOf("intellij.cross"),
        extraPluginModules = emptyList(),
        updateModules = listOf("intellij.cross"),
        refuseUpdate = { _, _ -> throw AssertionError("A configured module is no collected refusal") },
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("The update plugin 'intellij.cross' of 'idea' has no plugin layout")
  }

  @Test
  fun `the layout bindings collect a refused update request and keep the plugin of another product`() {
    val refused = request(layoutWithUnplacedResource(), DistPluginTier.ADDITIONAL).copy(updateOnly = true)
    val kept = request(plainLayout("intellij.unplaced"), DistPluginTier.BUNDLED).copy(product = "other")
    val refusals = DistUpdateRefusals()

    val outcome = bindGeneratedDistPluginLayouts(listOf(kept, refused), emptyIndex(), emptyOutputProvider(), CommunityDistHalf, refusals = refusals)

    assertThat(outcome.bindings.keys).containsExactly(kept.key)
    assertThat(outcome.dropped).isEmpty()
    assertThat(refusals.refused("idea")).containsExactly("intellij.unplaced")
    assertThat(refusals.refused("other")).isEmpty()
    // Without a collector, the same request stops the run.
    assertThatThrownBy { bindGeneratedDistPluginLayouts(listOf(kept, refused), emptyIndex(), emptyOutputProvider(), CommunityDistHalf) }
      .isInstanceOf(DistUnplannableLayoutException::class.java)
  }

  @Test
  fun `a registry module that is bundled or additional is refused`() {
    val properties = SyntheticProductProperties(persistentListOf(plainLayout("intellij.x")))

    assertThatThrownBy { registryRequests(properties, registryModules = listOf("intellij.x"), bundledPluginModules = listOf("intellij.x")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("Plugin 'intellij.x' of 'idea' is in the tiers [bundled, registry]")
    assertThatThrownBy { registryRequests(properties, registryModules = listOf("intellij.x"), additionalModules = listOf("intellij.x")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("Plugin 'intellij.x' of 'idea' is in the tiers [additional, registry]")
  }

  @Test
  fun `the layout bindings drop every variant of an unbindable registry layout and keep the run`() {
    val bundled = request(plainLayout("intellij.plain"), DistPluginTier.BUNDLED)
    // The mac variant of the registry layout binds, and the linux variant does not. The outcome drops both.
    val requests = listOf(
      bundled,
      request(layoutWithUnplacedResource(), DistPluginTier.REGISTRY),
      request(plainLayout("intellij.unplaced"), DistPluginTier.REGISTRY, variant = macAarch64),
    )

    val outcome = bindGeneratedDistPluginLayouts(
      requests = requests,
      index = emptyIndex(),
      outputProvider = emptyOutputProvider(),
      half = CommunityDistHalf,
      tolerated = { it.tier == DistPluginTier.REGISTRY },
    )

    assertThat(outcome.bindings.keys).containsExactly(bundled.key)
    assertThat(outcome.dropped).isEqualTo(mapOf(
      "intellij.unplaced" to "Plugin 'intellij.unplaced' declares the resource 'missing' of module 'intellij.unplaced', and the Bazel target index does not place that module",
    ))
  }

  @Test
  fun `the layout bindings still fail for an unbindable bundled or additional request`() {
    for (tier in DIST_COMPONENT_TIERS) {
      val requests = listOf(request(plainLayout("intellij.plain"), DistPluginTier.REGISTRY), request(layoutWithUnplacedResource(), tier))

      assertThatThrownBy {
        bindGeneratedDistPluginLayouts(requests, emptyIndex(), emptyOutputProvider(), CommunityDistHalf, tolerated = { it.tier == DistPluginTier.REGISTRY })
      }
        .describedAs(tier.name)
        .isInstanceOf(DistUnplannableLayoutException::class.java)
        .hasMessageStartingWith("Plugin 'intellij.unplaced' declares")
    }
  }

  @Test
  fun `a registry entry gets no component`() {
    val entries = listOf(
      Triple("idea", DistPluginTier.BUNDLED, "intellij.b"),
      Triple("idea", DistPluginTier.REGISTRY, "intellij.r"),
      Triple("idea", DistPluginTier.ADDITIONAL, "intellij.a"),
      Triple("server", DistPluginTier.REGISTRY, "intellij.r"),
    )
    val read = ArrayList<String>()

    val components = groupPluginComponents(entries, product = { it.first }, tier = { it.second }) { (product, _, mainModule) ->
      read.add("$product/$mainModule")
      GeneratedPluginComponent(mainModule, label = "//plugins:${mainModule}_dev_plugin")
    }

    assertThat(DIST_COMPONENT_TIERS).containsExactly(DistPluginTier.BUNDLED, DistPluginTier.ADDITIONAL)
    assertThat(read).containsExactly("idea/intellij.b", "idea/intellij.a")
    assertThat(components.mapValues { (_, tiers) -> tiers.mapValues { (_, ofTier) -> ofTier.map { it.mainModule } } }).isEqualTo(mapOf(
      "idea" to mapOf(DistPluginTier.BUNDLED to listOf("intellij.b"), DistPluginTier.ADDITIONAL to listOf("intellij.a")),
      "server" to mapOf(DistPluginTier.BUNDLED to emptyList(), DistPluginTier.ADDITIONAL to emptyList()),
    ))
  }

  @Test
  fun `a registry layout of two community products goes to the first split product`() {
    val assigned = assignRegistryLayoutsToProducts(
      registryPlugins = listOf("intellij.idea.only", "intellij.shared", "intellij.auto"),
      productOrder = CommunityDistHalf.splitProducts,
      layoutsByProduct = mapOf(
        "Idea" to setOf("intellij.idea.only", "intellij.shared"),
        "AndroidStudio" to setOf("intellij.shared"),
      ),
    )

    assertThat(CommunityDistHalf.splitProducts).containsExactly("AndroidStudio", "Idea")
    // A plugin without an explicit layout goes to the first split product, which gives it an automatic layout.
    assertThat(assigned).isEqualTo(mapOf("AndroidStudio" to listOf("intellij.shared", "intellij.auto"), "Idea" to listOf("intellij.idea.only")))
    assertThat(assigned.keys).containsExactly("AndroidStudio", "Idea")
  }

  @Test
  fun `the registry plugins leave out a test plugin, a requested plugin, and one without a package or a module`() {
    val project = JpsElementFactory.getInstance().createModel().project
    for (name in listOf("intellij.auto", "intellij.explicit", "intellij.bundled", "intellij.unplaced", "intellij.x.tests", "intellij.spec")) {
      project.addModule(name, JpsJavaModuleType.INSTANCE)
    }
    val modules = project.modules.associateBy { it.name }
    modules.getValue("intellij.x.tests").addSourceRoot(JpsPathUtil.pathToUrl(dir.resolve("x/tests/testSrc").toString()), JavaSourceRootType.TEST_SOURCE)

    val registryPlugins = distRegistryPlugins(
      population = listOf("intellij.x.tests", "intellij.bundled", "intellij.auto", "intellij.unplaced", "intellij.noModule", "intellij.spec"),
      registryLayouts = listOf("intellij.explicit", "intellij.auto"),
      composed = setOf("intellij.bundled"),
      isPlaced = { it != "intellij.unplaced" },
      findModule = modules::get,
      // A module that matches a Product DSL test plugin, see resolveDistTestPlugins.
      testPluginModules = setOf("intellij.spec"),
    )

    assertThat(registryPlugins).containsExactly("intellij.auto", "intellij.explicit")
  }

  @Test
  fun `a registry plugin goes to the first split product of the run`() {
    val assigned = assignRegistryLayoutsToProducts(
      registryPlugins = listOf("intellij.auto"),
      productOrder = CommunityDistHalf.splitProducts,
      layoutsByProduct = mapOf("Idea" to emptySet()),
    )

    assertThat(assigned).isEqualTo(mapOf("Idea" to listOf("intellij.auto")))
    assertThat(assignRegistryLayoutsToProducts(listOf("intellij.auto"), CommunityDistHalf.splitProducts, layoutsByProduct = emptyMap())).isEmpty()
  }

  @Test
  fun `a population plugin without an explicit layout becomes a registry request with an automatic layout`() {
    val properties = SyntheticProductProperties(persistentListOf(plainLayout("intellij.bundled")))

    val requests = registryRequests(properties, registryModules = listOf("intellij.auto"), bundledPluginModules = listOf("intellij.bundled"))

    assertThat(requests.map { it.layout.mainModule to it.tier }).containsExactly(
      "intellij.bundled" to DistPluginTier.BUNDLED,
      "intellij.auto" to DistPluginTier.REGISTRY,
    )
    val auto = requests.single { it.tier == DistPluginTier.REGISTRY }.layout
    assertThat(properties.productLayout.pluginLayouts.value).doesNotContain(auto)
  }

  /** The plan entry of [layout] with the facts the layout states and no descriptor row. */
  private fun descriptorEntry(layout: PluginLayout): PluginDescriptorEntry {
    val facts = statedDescriptorFacts(layout.mainModule, "", layout, null, null)
    return PluginDescriptorEntry(
      mainModule = layout.mainModule,
      variant = "",
      moduleTarget = "@community//plugins/x:x",
      descriptor = "resources/META-INF/plugin.xml",
      refusedContentModules = emptyList(),
      separateJar = emptyList(),
      descriptors = emptyList<DeclaredDescriptor>(),
      includeDescriptors = emptyList<DescriptorPathRow>(),
      libraryDescriptorRows = emptyList<LibraryDescriptorRow>(),
      libraryDescriptors = emptyList<DeclaredLibraryDescriptor>(),
      markers = facts.markers,
      versionSuffix = facts.versionSuffix,
      compatibleBuildRange = facts.compatibleBuildRange,
      derivesOsArchStamps = facts.derivesOsArchStamps,
      embedsContentModules = true,
      exactVersion = false,
      retainProductDescriptor = false,
      directoryName = null,
      layout = layout,
    )
  }

  private fun descriptorPlan(product: String, layout: PluginLayout): PluginDescriptorPlan {
    return PluginDescriptorPlan(platformPrefix = product, plugins = listOf(descriptorEntry(layout)))
  }

  /** The verdicts of a run in which the own package of `intellij.x` declares [ownLeaf], or no leaf for `null`. */
  private fun verdicts(ownLeaf: String?): DistToolVerdicts {
    val pluginRecords = LinkedHashMap<String, DevSectionRecord>()
    if (ownLeaf != null) {
      pluginRecords.put("intellij.x", DevSectionRecord(descriptorTargets = mapOf("" to ownLeaf)))
    }
    return DistToolVerdicts(contentModuleJarLabels = emptyMap<String, DistModuleJarArtifact>(), pluginRecords = pluginRecords, population = setOf("intellij.x"))
  }

  private val pluginPackage = "build/dist-descriptors/intellij.x/BUILD.bazel"

  /** A layout of `intellij.x` whose descriptor patch states a marker, so its entry differs from a plain one. */
  private fun markedLayout(): PluginLayout {
    return PluginLayout.pluginAuto("intellij.x") { spec ->
      spec.withRawPluginXmlPatcher(DescriptorMarkerPatcher(listOf(DescriptorMarker("<idea-plugin>", "<idea-plugin implementation-detail=\"true\">"))))
    }
  }

  @Test
  fun `two products that state one plugin alike share the plugin's one home`() {
    val plans = listOf<PluginDescriptorPlan>(descriptorPlan("idea", plainLayout("intellij.x")), descriptorPlan("server", plainLayout("intellij.x")))

    val classes = computeDescriptorResidueClasses(plans, productOrder = listOf("idea", "server"))
    val packages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = null), classes, planLabel = { it }, conventionalLabel = { null }, half = CommunityDistHalf)

    val residue = classes.getValue("intellij.x")
    assertThat(residue.classes.size).isEqualTo(1)
    assertThat(residue.isBaseline("idea")).isTrue()
    assertThat(residue.isBaseline("server")).isTrue()
    assertThat(residue.divergent).isEmpty()
    assertThat(packages.files(emptyMap()).keys).containsExactly(pluginPackage)
    for (plan in plans) {
      val entry: PluginDescriptorEntry = plan.plugins.single()
      assertThat(packages.sharedDeclaration(entry)?.label).isEqualTo("//build/dist-descriptors/intellij.x:intellij.x_dev_descriptor")
      assertThat(packages.productDeclaration(plan.platformPrefix, entry)).isNull()
    }
  }

  @Test
  fun `a cross-half leaf states the conventional row of each content module in the recorded form`() {
    val plain = descriptorEntry(plainLayout("intellij.x"))
    val a = "@community//plugins/x/a:resources/intellij.x.a.xml"
    val entry = PluginDescriptorEntry(
      mainModule = plain.mainModule,
      variant = "",
      moduleTarget = plain.moduleTarget,
      descriptor = plain.descriptor,
      refusedContentModules = emptyList(),
      separateJar = emptyList(),
      descriptors = listOf(
        DeclaredDescriptor(loadPath = "intellij.x.a.xml", label = a, moduleName = "intellij.x.a"),
        DeclaredDescriptor(loadPath = "intellij.x.b.xml", label = "@community//plugins/x/b:other/intellij.x.b.xml", moduleName = "intellij.x.b"),
        DeclaredDescriptor(loadPath = "META-INF/x-extra.xml", label = "@community//plugins/x:resources/META-INF/x-extra.xml", moduleName = "intellij.x"),
        DeclaredDescriptor(loadPath = "intellij.y.xml", label = "@community//plugins/y:resources/intellij.y.xml", moduleName = "intellij.y"),
      ),
      includeDescriptors = emptyList(),
      libraryDescriptorRows = emptyList(),
      libraryDescriptors = emptyList(),
      markers = plain.markers,
      versionSuffix = plain.versionSuffix,
      compatibleBuildRange = plain.compatibleBuildRange,
      derivesOsArchStamps = plain.derivesOsArchStamps,
      embedsContentModules = true,
      exactVersion = false,
      retainProductDescriptor = false,
      directoryName = null,
      layout = plain.layout,
      contentModules = listOf(DeclaredContentModule("intellij.x.b", isOptional = false), DeclaredContentModule("intellij.x.a", isOptional = true)),
    )
    val conventionalLabels = mapOf(
      "intellij.x.a" to a,
      "intellij.x.b" to "@community//plugins/x/b:resources/intellij.x.b.xml",
      "intellij.y" to "@community//plugins/y:resources/intellij.y.xml",
    )
    val classes = computeDescriptorResidueClasses(listOf(PluginDescriptorPlan(platformPrefix = "idea", plugins = listOf(entry))), productOrder = listOf("idea"))
    val packages = collectCrossHalfDescriptorPackages(
      verdicts = verdicts(ownLeaf = null),
      classes = classes,
      planLabel = { it.replace("@community//", "//") },
      conventionalLabel = conventionalLabels::get,
      half = CommunityDistHalf,
    )

    val text = packages.files(pluginTargets = mapOf(pluginPackage to "dev_plugin(name = \"intellij.x_dev_plugin\")\n")).getValue(pluginPackage)
    // The package loads the macro files in the spelling of its half, and buildifier sorts the load lines by label.
    assertThat(text).contains(
      "load(\"//platform/build-scripts/bazel-rules:dev_plugin.bzl\", \"dev_plugin\")\n" +
      "load(\"//platform/build-scripts/bazel-rules:dist_plugin_descriptor.bzl\", \"dist_plugin_descriptor\")\n",
    )
    // `intellij.x.a` gets its conventional row in the recorded form. `intellij.x.b` keeps the row at its load path, and
    // `intellij.y` is no content module, so its row stays as the closure gives it. A row whose load path is the base
    // name of the file goes to `module_descriptors`.
    assertThat(text).contains(
      "dist_plugin_descriptor(\n" +
      "    descriptor = \"//plugins/x:resources/META-INF/plugin.xml\",\n" +
      "    descriptors = {\n" +
      "        \"//plugins/x:resources/META-INF/x-extra.xml\": \"META-INF/x-extra.xml\",\n" +
      "    },\n",
    )
    assertThat(text).contains(
      "    module_descriptors = [\n" +
      "        \"//plugins/x/b:other/intellij.x.b.xml\",\n" +
      "        \"//plugins/y:resources/intellij.y.xml\",\n" +
      "        \"@community//plugins/x/a:resources/intellij.x.a.xml\",\n" +
      "    ],\n",
    )
    assertThat(text).doesNotContain("content_modules")
  }

  @Test
  fun `a conventional row is the row of a content module at its conventional descriptor label`() {
    val moduleName = "intellij.test.content"
    val label = "@community//plugins/test/content:first/$moduleName.xml"
    val conventionalLabel: (String) -> String? = { if (it == moduleName) label else null }

    assertThat(isConventionalDescriptorRow(label, "$moduleName.xml", listOf(moduleName), conventionalLabel)).isTrue()
    // Another label, another load path, or a module outside the content modules keeps the row as it is.
    assertThat(isConventionalDescriptorRow("@community//plugins/test/content:second/$moduleName.xml", "$moduleName.xml", listOf(moduleName), conventionalLabel)).isFalse()
    assertThat(isConventionalDescriptorRow(label, "META-INF/$moduleName.xml", listOf(moduleName), conventionalLabel)).isFalse()
    assertThat(isConventionalDescriptorRow(label, "$moduleName.xml", listOf("intellij.test.other"), conventionalLabel)).isFalse()
    assertThat(isConventionalDescriptorRow(label, moduleName, listOf(moduleName), conventionalLabel)).isFalse()
  }

  @Test
  fun `a stated row wins over the conventional row by label and by load path`() {
    val conventionalLabels = mapOf(
      "intellij.a" to "//a:resources/intellij.a.xml",
      "intellij.b" to "//b:resources/intellij.b.xml",
      "intellij.c" to "//c:resources/intellij.c.xml",
    )
    val stated = mapOf(
      "//a:resources/intellij.a.xml" to "META-INF/a.xml",
      "//other:intellij.b.xml" to "intellij.b.xml",
    )

    assertThat(withConventionalDescriptorRows(stated, listOf("intellij.a", "intellij.b", "intellij.c", "intellij.d"), conventionalLabels::get))
      .containsExactly(
        entry("//a:resources/intellij.a.xml", "META-INF/a.xml"),
        entry("//c:resources/intellij.c.xml", "intellij.c.xml"),
        entry("//other:intellij.b.xml", "intellij.b.xml"),
      )
  }

  @Test
  fun `a row whose load path is the base name of the file goes to the module descriptors`() {
    val rows = splitDescriptorRows(mapOf(
      "//b:resources/intellij.b.xml" to "intellij.b.xml",
      "//a:resources/META-INF/a.xml" to "META-INF/a.xml",
      "//other:intellij.c.xml" to "intellij.c.xml",
      "//d:resources/intellij.d.xml" to "intellij.other.xml",
    ))

    assertThat(rows.moduleDescriptors).containsExactly("//b:resources/intellij.b.xml", "//other:intellij.c.xml")
    assertThat(rows.descriptors).containsExactly(
      entry("//a:resources/META-INF/a.xml", "META-INF/a.xml"),
      entry("//d:resources/intellij.d.xml", "intellij.other.xml"),
    )
  }

  @Test
  fun `a product that states a plugin differently gets a product home and the baseline keeps the plugin's home`() {
    val plans = listOf<PluginDescriptorPlan>(descriptorPlan("idea", plainLayout("intellij.x")), descriptorPlan("server", markedLayout()))
    val ideaEntry: PluginDescriptorEntry = plans[0].plugins.single()
    val serverEntry: PluginDescriptorEntry = plans[1].plugins.single()
    val serverPackage = "build/dist-descriptors/intellij.x/server/BUILD.bazel"

    val classes = computeDescriptorResidueClasses(plans, productOrder = listOf("idea", "server"))
    val packages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = null), classes, planLabel = { it }, conventionalLabel = { null }, half = CommunityDistHalf)

    val residue = classes.getValue("intellij.x")
    assertThat(residue.classes.size).isEqualTo(2)
    assertThat(residue.isBaseline("idea")).isTrue()
    assertThat(residue.isBaseline("server")).isFalse()
    assertThat(residue.divergent.map { it.product }).containsExactly("server")
    val files = packages.files(emptyMap())
    assertThat(files.keys).containsExactly(pluginPackage, serverPackage)
    assertThat(files.getValue(pluginPackage)).doesNotContain("markers")
    // The marker row carries the raw descriptor text, so its quotes render escaped.
    assertThat(files.getValue(serverPackage))
      .contains("for the product `server`")
      .contains("markers = [\n        \"marker:<idea-plugin>:<idea-plugin implementation-detail=\\\"true\\\">\",\n    ],\n")
      .contains("main_module = \"intellij.x\"")
    assertThat(packages.sharedDeclaration(ideaEntry)?.label).isEqualTo("//build/dist-descriptors/intellij.x:intellij.x_dev_descriptor")
    assertThat(packages.productDeclaration("idea", ideaEntry)).isNull()
    assertThat(packages.productDeclaration("server", serverEntry)?.label).isEqualTo("//build/dist-descriptors/intellij.x/server:intellij.x_dev_descriptor")

    // A plugin whose own package declares the leaf keeps that leaf for the baseline, and the product home is the same.
    val ownPackages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = "@community//plugins/x:intellij.x_dev_descriptor"), classes, planLabel = { it }, conventionalLabel = { null }, half = CommunityDistHalf)
    assertThat(ownPackages.files(emptyMap()).keys).containsExactly(serverPackage)
    assertThat(ownPackages.sharedDeclaration(ideaEntry)).isNull()
    assertThat(ownPackages.productDeclaration("server", serverEntry)?.label).isEqualTo("//build/dist-descriptors/intellij.x/server:intellij.x_dev_descriptor")

    // The product order decides the baseline, and the sorted plan order does not.
    val reversed = computeDescriptorResidueClasses(plans, productOrder = listOf("server", "idea")).getValue("intellij.x")
    assertThat(reversed.isBaseline("server")).isTrue()
    assertThat(reversed.divergent.map { it.product }).containsExactly("idea")
  }

  /**
   * The plan of [product] in [mode], with one entry of `intellij.x` over its two content modules. The entry states the
   * module the frontend mode refuses, as every product's entry of the plugin does, and refuses nothing at build time.
   */
  private fun modePlan(product: String, mode: String, marked: Boolean = false): PluginDescriptorPlan {
    val all = listOf("intellij.x.backend", "intellij.x.shared")
    val base: PluginDescriptorEntry = descriptorEntry(if (marked) markedLayout() else plainLayout("intellij.x"))
    val entry = PluginDescriptorEntry(
      mainModule = base.mainModule,
      variant = "",
      moduleTarget = base.moduleTarget,
      descriptor = base.descriptor,
      refusedContentModules = emptyList(),
      separateJar = emptyList(),
      descriptors = all.map { DeclaredDescriptor("$it.xml", "@community//plugins/x:$it.xml", moduleName = it) },
      includeDescriptors = emptyList<DescriptorPathRow>(),
      libraryDescriptorRows = emptyList<LibraryDescriptorRow>(),
      libraryDescriptors = emptyList<DeclaredLibraryDescriptor>(),
      markers = base.markers,
      versionSuffix = base.versionSuffix,
      compatibleBuildRange = base.compatibleBuildRange,
      derivesOsArchStamps = base.derivesOsArchStamps,
      embedsContentModules = true,
      exactVersion = false,
      retainProductDescriptor = false,
      directoryName = null,
      layout = base.layout,
      contentModules = all.map { DeclaredContentModule(it, true, null) },
      modeRefusedContentModules = mapOf("frontend" to listOf("intellij.x.backend")),
    )
    return PluginDescriptorPlan(platformPrefix = product, mode = mode, plugins = listOf(entry))
  }

  @Test
  fun `a frontend product whose entry is the monolith entry shares the baseline leaf`() {
    val plans: List<PluginDescriptorPlan> = listOf(
      modePlan("idea", "monolith"),
      modePlan("client", "frontend"),
      modePlan("other", "frontend"),
    )

    val classes: DescriptorResidueClasses = computeDescriptorResidueClasses(plans, productOrder = listOf("idea", "client", "other")).getValue("intellij.x")

    assertThat(classes.classes).hasSize(1)
    assertThat(classes.isBaseline("client")).isTrue()
    assertThat(classes.home("other")).isNull()
    val leaf: PluginDescriptorEntry = classes.baseline
    // The leaf keeps every module and states the mode list. The rules place no jar of a refused module under the mode.
    assertThat(leaf.refusedContentModules).isEmpty()
    assertThat(leaf.modeRefusedContentModules).isEqualTo(mapOf("frontend" to listOf("intellij.x.backend")))
    assertThat(leaf.descriptors.map { it.moduleName }).containsExactly("intellij.x.backend", "intellij.x.shared")
    assertThat(leaf.contentModules.map { it.name }).containsExactly("intellij.x.backend", "intellij.x.shared")
  }

  @Test
  fun `a frontend product that differs beyond the mode keeps a residue class of its own`() {
    val plans: List<PluginDescriptorPlan> = listOf(
      modePlan("idea", "monolith"),
      modePlan("client", "frontend", marked = true),
    )

    val classes: DescriptorResidueClasses = computeDescriptorResidueClasses(plans, productOrder = listOf("idea", "client")).getValue("intellij.x")

    assertThat(classes.classes).hasSize(2)
    assertThat(classes.home("client")).isEqualTo("client")
  }

  @Test
  fun `the sweep names every descriptor package on disk that the run does not write`() {
    val plans = listOf<PluginDescriptorPlan>(descriptorPlan("idea", plainLayout("intellij.x")))
    val classes = computeDescriptorResidueClasses(plans, productOrder = listOf("idea"))
    val packages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = null), classes, planLabel = { it }, conventionalLabel = { null }, half = CommunityDistHalf)
    // On disk: the root package, the written plugin package, and a file that is no package. Also a product that left
    // the split products, and a plugin that left the population with a product package of its own.
    for (relativePath in listOf("BUILD.bazel", "intellij.x/BUILD.bazel", "intellij.x/gone/BUILD.bazel", "intellij.y/BUILD.bazel", "intellij.y/idea/BUILD.bazel", "intellij.z/notes.txt")) {
      val file = dir.resolve("build/dist-descriptors").resolve(relativePath)
      Files.createDirectories(file.parent)
      Files.writeString(file, "")
    }

    assertThat(packages.files(emptyMap()).keys).containsExactly(pluginPackage)
    assertThat(packages.stale(dir, emptyMap())).containsExactly(
      "build/dist-descriptors/intellij.x/gone/BUILD.bazel",
      "build/dist-descriptors/intellij.y/BUILD.bazel",
      "build/dist-descriptors/intellij.y/idea/BUILD.bazel",
    )
    assertThat(packages.stale(dir.resolve("no-checkout"), emptyMap())).isEmpty()
  }

  @Test
  fun `a product without a composition order keeps the request order of its bundled components`() {
    val components = ArrayList<GeneratedPluginComponent>()
    for (mainModule in listOf("intellij.c", "intellij.b", "intellij.a")) {
      components.add(GeneratedPluginComponent(mainModule, label = "//plugins:${mainModule}_dev_plugin"))
    }

    val composed = composedBundledComponents(product = "synthetic", compositionOrder = emptyList(), components = components)

    assertThat(composed.map { it.mainModule }).containsExactly("intellij.c", "intellij.b", "intellij.a")
  }
}

/** A product with the given plugin layouts and no bundled plugin. */
internal class SyntheticProductProperties(layouts: List<PluginLayout> = emptyList()) : ProductProperties() {
  init {
    productLayout.bundledPluginModules = persistentListOf()
    productLayout.pluginLayouts = lazyOf(layouts.toPersistentList())
  }

  override val baseFileName: String = "synthetic"
  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "synthetic"
  override fun createWindowsCustomizer(projectHome: Path) = null
  override fun createLinuxCustomizer(projectHome: Path) = null
  override fun createMacCustomizer(projectHome: Path) = null
  override fun getProductContentDescriptor() = null
}
