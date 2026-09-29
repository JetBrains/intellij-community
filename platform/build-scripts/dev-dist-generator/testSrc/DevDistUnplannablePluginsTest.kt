// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The rejection sites of the dev-distribution plan over synthetic layouts: each stops the run, for a bundled and an
 * additional plugin alike. Also the divergence rule: two products that plan one plugin share one home or give the later
 * product a product home.
 */
class DevDistUnplannablePluginsTest {
  @TempDir
  lateinit var dir: Path

  private val versionAsCode = PluginVersionEvaluator { _, context ->
    PluginVersionEvaluatorResult(pluginVersion = "${context.pluginBuildNumber}-code")
  }

  private val linuxX64 = PluginSymbolicVariant(id = "linux_x64", distribution = SUPPORTED_DISTRIBUTIONS.single { it.os == OsFamily.LINUX && it.arch == JvmArchitecture.x64 })

  private fun layoutWithVersionAsCode(): PluginLayout = PluginLayout.pluginAutoWithCustomDirName("intellij.x") { it.withCustomVersion(versionAsCode) }

  /** A layout with a resource of a module that the Bazel target index does not place, so no binding can state it. */
  private fun layoutWithUnplacedResource(mainModule: String = "intellij.unplaced"): PluginLayout {
    return PluginLayout.pluginAutoWithCustomDirName(mainModule) { it.withResource("missing", "missing") }
  }

  private fun plainLayout(mainModule: String): PluginLayout = PluginLayout.pluginAutoWithCustomDirName(mainModule) { }

  /** A layout that excludes a directory from its module jar. The dev distribution has no module filter, so no binding states it. */
  private fun layoutWithModuleExcludes(mainModule: String = "intellij.filtered"): PluginLayout {
    return PluginLayout.pluginAutoWithCustomDirName(mainModule) { it.excludeFromModule(mainModule, "server/**") }
  }

  private fun request(layout: PluginLayout, tier: DevDistPluginTier): DevDistPluginRequest {
    return DevDistPluginRequest(product = "idea", properties = SyntheticProductProperties(), tier = tier, variant = linuxX64, layout = layout)
  }

  private fun emptyIndex(): DevDistBazelIndex {
    val targets = BazelTargetsInfo.TargetsFile(modules = emptyMap(), projectLibraries = emptyMap(), pluginDistributionTargets = emptyMap())
    return DevDistBazelIndex(targets = targets, projectRoot = dir)
  }

  private fun emptyOutputProvider(): ModuleOutputProvider {
    return JpsModuleOutputProviderState(JpsElementFactory.getInstance().createModel().project).createProvider(useTestCompilationOutput = false)
  }

  private fun rejection(layout: PluginLayout, variant: String, os: OsFamily?, arch: JvmArchitecture?): DevDistUnplannableLayoutException {
    return assertThrows<DevDistUnplannableLayoutException> { statedDescriptorFacts(layout.mainModule, variant, layout, os, arch) }
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
    val layout = PluginLayout.pluginAutoWithCustomDirName("intellij.x") { it.withCustomVersion(SuffixedPluginVersion("-suffix")) }

    val facts = statedDescriptorFacts(layout.mainModule, "", layout, null, null)

    assertThat(facts.versionSuffix).isEqualTo("-suffix")
    assertThat(facts.markers).isEmpty()
    assertThat(facts.derivesOsArchStamps).isFalse()
  }

  @Test
  fun `the layout bindings fail for a plugin with an unplaced resource in either tier`() {
    for (tier in DevDistPluginTier.entries) {
      val requests = listOf(request(plainLayout("intellij.plain"), tier), request(layoutWithUnplacedResource(), tier))

      assertThatThrownBy { bindGeneratedDevDistPluginLayouts(requests, emptyIndex(), emptyOutputProvider(), CommunityDevDistHalf) }
        .describedAs(tier.name)
        .isInstanceOf(DevDistUnplannableLayoutException::class.java)
        .hasMessage("Plugin 'intellij.unplaced' declares the resource 'missing' of module 'intellij.unplaced', and the Bazel target index does not place that module")
    }
  }

  @Test
  fun `the layout bindings fail for a plugin that excludes a directory from a module jar`() {
    for (tier in DevDistPluginTier.entries) {
      val requests = listOf(request(plainLayout("intellij.plain"), tier), request(layoutWithModuleExcludes(), tier))

      assertThatThrownBy { bindGeneratedDevDistPluginLayouts(requests, emptyIndex(), emptyOutputProvider(), CommunityDevDistHalf) }
        .describedAs(tier.name)
        .isInstanceOf(DevDistUnplannableLayoutException::class.java)
        .hasMessage(
          "Plugin 'intellij.filtered' excludes [server/**] from the module 'intellij.filtered'. " +
          "The dev distribution has no module filter: move the directory out of the resource root and copy it with withResource."
        )
    }
  }

  @Test
  fun `the layout bindings name the first rejected request in request order`() {
    val requests = listOf("intellij.b", "intellij.a", "intellij.c").map { request(layoutWithUnplacedResource(it), DevDistPluginTier.ADDITIONAL) }

    assertThatThrownBy { bindGeneratedDevDistPluginLayouts(requests, emptyIndex(), emptyOutputProvider(), CommunityDevDistHalf) }
      .isInstanceOf(DevDistUnplannableLayoutException::class.java)
      .hasMessageStartingWith("Plugin 'intellij.b' declares")
  }

  @Test
  fun `a run-configuration module without a layout or a platform variant fails like a hand extra without a layout`() {
    val crossPlatformOnly = PluginLayout.pluginAutoWithCustomDirName("intellij.cross") {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }
    val macOnly = PluginLayout.pluginAutoWithCustomDirName("intellij.mac") {
      it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    }
    val properties = SyntheticProductProperties(persistentListOf(crossPlatformOnly, macOnly, plainLayout("intellij.plain")))
    fun requests(additionalModules: List<String> = emptyList(), extraPluginModules: List<String> = emptyList()): List<DevDistPluginRequest> {
      return enumerateDevDistPluginRequests(
        product = "idea",
        properties = properties,
        bundledPluginModules = emptyList(),
        variants = listOf(linuxX64),
        additionalModules = additionalModules,
        extraPluginModules = extraPluginModules,
      )
    }

    assertThat(requests(additionalModules = listOf("intellij.plain")).map { it.layout.mainModule to it.tier })
      .containsExactly("intellij.plain" to DevDistPluginTier.ADDITIONAL)
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
    return PluginDescriptorPlan(platformPrefix = product, releaseDate = "", releaseVersion = "", eap = false, plugins = listOf(descriptorEntry(layout)))
  }

  /** The verdicts of a run in which the own package of `intellij.x` declares [ownLeaf], or no leaf for `null`. */
  private fun verdicts(ownLeaf: String?): DevDistToolVerdicts {
    val pluginRecords = LinkedHashMap<String, DevSectionRecord>()
    if (ownLeaf != null) {
      pluginRecords.put("intellij.x", DevSectionRecord(descriptorTargets = mapOf("" to ownLeaf)))
    }
    return DevDistToolVerdicts(contentModuleJarLabels = emptyMap<String, DevDistModuleJarArtifact>(), pluginRecords = pluginRecords, population = setOf("intellij.x"))
  }

  private val pluginPackage = "build/dev-dist-descriptors/intellij.x/BUILD.bazel"

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
    val packages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = null), classes)

    val residue = classes.getValue("intellij.x")
    assertThat(residue.classes.size).isEqualTo(1)
    assertThat(residue.isBaseline("idea")).isTrue()
    assertThat(residue.isBaseline("server")).isTrue()
    assertThat(residue.divergent).isEmpty()
    assertThat(packages.files(emptyMap()).keys).containsExactly(pluginPackage)
    for (plan in plans) {
      val entry: PluginDescriptorEntry = plan.plugins.single()
      assertThat(packages.sharedDeclaration(entry)?.label).isEqualTo("//build/dev-dist-descriptors/intellij.x:intellij.x_dev_descriptor")
      assertThat(packages.productDeclaration(plan.platformPrefix, entry)).isNull()
    }
  }

  @Test
  fun `a product that states a plugin differently gets a product home and the baseline keeps the plugin's home`() {
    val plans = listOf<PluginDescriptorPlan>(descriptorPlan("idea", plainLayout("intellij.x")), descriptorPlan("server", markedLayout()))
    val ideaEntry: PluginDescriptorEntry = plans[0].plugins.single()
    val serverEntry: PluginDescriptorEntry = plans[1].plugins.single()
    val serverPackage = "build/dev-dist-descriptors/intellij.x/server/BUILD.bazel"

    val classes = computeDescriptorResidueClasses(plans, productOrder = listOf("idea", "server"))
    val packages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = null), classes)

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
    assertThat(packages.sharedDeclaration(ideaEntry)?.label).isEqualTo("//build/dev-dist-descriptors/intellij.x:intellij.x_dev_descriptor")
    assertThat(packages.productDeclaration("idea", ideaEntry)).isNull()
    assertThat(packages.productDeclaration("server", serverEntry)?.label).isEqualTo("//build/dev-dist-descriptors/intellij.x/server:intellij.x_dev_descriptor")

    // A plugin whose own package declares the leaf keeps that leaf for the baseline, and the product home is the same.
    val ownPackages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = "@community//plugins/x:intellij.x_dev_descriptor"), classes)
    assertThat(ownPackages.files(emptyMap()).keys).containsExactly(serverPackage)
    assertThat(ownPackages.sharedDeclaration(ideaEntry)).isNull()
    assertThat(ownPackages.productDeclaration("server", serverEntry)?.label).isEqualTo("//build/dev-dist-descriptors/intellij.x/server:intellij.x_dev_descriptor")

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
    return PluginDescriptorPlan(platformPrefix = product, releaseDate = "", releaseVersion = "", eap = false, mode = mode, plugins = listOf(entry))
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
    val packages = collectCrossHalfDescriptorPackages(verdicts(ownLeaf = null), classes)
    // On disk: the root package, the written plugin package, and a file that is no package. Also a product that left
    // the split products, and a plugin that left the population with a product package of its own.
    for (relativePath in listOf("BUILD.bazel", "intellij.x/BUILD.bazel", "intellij.x/gone/BUILD.bazel", "intellij.y/BUILD.bazel", "intellij.y/idea/BUILD.bazel", "intellij.z/notes.txt")) {
      val file = dir.resolve("build/dev-dist-descriptors").resolve(relativePath)
      Files.createDirectories(file.parent)
      Files.writeString(file, "")
    }

    assertThat(packages.files(emptyMap()).keys).containsExactly(pluginPackage)
    assertThat(packages.stale(dir, emptyMap())).containsExactly(
      "build/dev-dist-descriptors/intellij.x/gone/BUILD.bazel",
      "build/dev-dist-descriptors/intellij.y/BUILD.bazel",
      "build/dev-dist-descriptors/intellij.y/idea/BUILD.bazel",
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
private class SyntheticProductProperties(layouts: List<PluginLayout> = emptyList()) : ProductProperties() {
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
