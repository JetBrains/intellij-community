// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifact
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifactCatalogue
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicLibrary
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.dev.DevPluginPreparationRecipe
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.ReusableJarArtifact
import org.jetbrains.intellij.build.devDist.pluginPackingLayoutSignature
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.intellij.build.productLayout.model.error.FileChangeType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The plan files of a plugin two products plan, over synthetic neutral and platform records, and the plan home of an
 * ultimate plugin, of a community plugin, and of a community plugin whose plan names another repository.
 */
class DevDistPluginPlanFilesTest {
  @TempDir
  lateinit var dir: Path

  /** The ultimate plugin of the synthetic index, in `//plugins/x`. */
  private val plugin = "intellij.x"

  /** The community plugin of the synthetic index, in `@community//plugins/c`. */
  private val communityPlugin = "intellij.c"

  private val planDirectory = "plugins/x"
  private val planPackage = "//plugins/x"

  /** The two platforms of a platform record pair, in `HOST_PLATFORMS` order. */
  private val platforms = listOf("darwin_aarch64", "linux_x64")

  private lateinit var index: DevDistBazelIndex

  @BeforeEach
  fun createIndex() {
    index = syntheticIndex(
      dir,
      plugin to "//plugins/x:x.jar",
      communityPlugin to "@community//plugins/c:c.jar",
      "intellij.noBuildFile" to "//plugins/no-build-file:no-build-file.jar",
    )
    for (packagePath in listOf("plugins/x", "community/plugins/c")) {
      Files.writeString(Files.createDirectories(dir.resolve(packagePath)).resolve("BUILD.bazel"), "")
    }
  }

  /** A complete neutral record of one jar over the main module. Two records with one [destination] have one text. */
  private fun record(destination: String, plugin: String = this.plugin, library: String? = null): DevDistPluginPlanRecord {
    return record(listOf(destination), variant = "", plugin = plugin, library = library)
  }

  /**
   * A complete record of [variant] with one jar per destination over the main module. Two records of one variant with
   * one destination list have one text. Two variants with one destination list fold, and two with a different number
   * of destinations differ in shape. A non-null [library] is a library source of every jar, so the plan text names it.
   */
  private fun record(destinations: List<String>, variant: String, plugin: String = this.plugin, library: String? = null): DevDistPluginPlanRecord {
    val sources = listOfNotNull(
      JarSourceRecipe(input = plugin, kind = "module", filter = "module-v1"),
      library?.let { JarSourceRecipe(input = it, kind = "library", filter = "library-v1") },
    )
    val recipe = CanonicalJarRecipe(sources = sources)
    val inputs = listOfNotNull(plugin, library)
    val assets = destinations.map { PluginPackingAsset(destination = it, inputs = inputs, recipe = recipe) }
    val signature = pluginPackingLayoutSignature(plugin, variant, assets, emptyList(), emptyList())
    val plan = object : DevDistPluginBuildPlan {
      override val projection = PluginPackingProjection(plugin = plugin, variant = variant, layoutSignature = signature, assets = assets)
      override val catalogue = PluginSymbolicArtifactCatalogue(
        artifacts = listOf(PluginSymbolicArtifact(id = plugin, kind = "directory", fileName = plugin)),
        moduleRoots = mapOf(plugin to listOf(plugin)),
        libraries = listOfNotNull(library?.let { PluginSymbolicLibrary(libraryName = "lib", files = listOf("lib.jar"), id = it) }),
      )
      override val requiredRawInputs = listOf(DevDistPluginRawInput(id = plugin, label = "//plugins/x:x", kind = "directory", fileName = plugin))
      override val requiredLibraries = listOfNotNull(library)
      override val reusableArtifacts = emptyList<ReusableJarArtifact>()
      override val layoutSignature = signature
    }
    return DevDistPluginPlanRecord(variant = PluginSymbolicVariant(id = variant), plan = plan, preparationRecipe = DevPluginPreparationRecipe(operations = emptyList()))
  }

  private fun key(product: String, variant: String = "", plugin: String = this.plugin): DevDistPluginPlanKey {
    return DevDistPluginPlanKey(product = product, plugin = plugin, variant = variant)
  }

  /** Adds one platform record per platform for [product] to [result]. [destinations] gives the jars of a platform. */
  private fun addPlatformRecords(
    result: MutableMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>,
    product: String,
    destinations: (String) -> List<String>,
  ) {
    for (platform in platforms) {
      result.put(key(product, platform), record(destinations(platform), variant = platform))
    }
  }

  /** The labels of every platform key of [product], checked against the emitted files. */
  private fun platformLabels(
    files: DevDistPluginPlanFiles,
    records: Map<DevDistPluginPlanKey, DevDistPluginPlanRecord>,
    product: String,
  ): Map<String, DevDistPluginExecutionGraphLabels> {
    val result = LinkedHashMap<String, DevDistPluginExecutionGraphLabels>()
    for (platform in platforms) {
      val record: DevDistPluginPlanRecord = records.getValue(key(product, platform))
      val labels: DevDistPluginExecutionGraphLabels = files.executionGraphLabels(key(product, platform), record)
      files.checkExecutionGraph(key(product, platform), record, labels)
      result.put(platform, labels)
    }
    return result
  }

  /** The records of `idea` and `server`. The `idea` record packs `lib/x.jar`, and the `server` record packs [serverDestination]. */
  private fun records(serverDestination: String): Map<DevDistPluginPlanKey, DevDistPluginPlanRecord> {
    val result = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>()
    result.put(key("idea"), record("lib/x.jar"))
    result.put(key("server"), record(serverDestination))
    return result
  }

  /** One neutral `idea` record of [plugin], with a library source when [library] is not null. */
  private fun neutralRecords(plugin: String, library: String? = null): Map<DevDistPluginPlanKey, DevDistPluginPlanRecord> {
    return mapOf(key("idea", plugin = plugin) to record("lib/c.jar", plugin = plugin, library = library))
  }

  @Test
  fun `two products with one plan text share one plan file`() {
    val records: Map<DevDistPluginPlanKey, DevDistPluginPlanRecord> = records(serverDestination = "lib/x.jar")

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))

    assertThat(files.files.keys).containsExactly("$planDirectory/$plugin.dev-plan.json")
    for (product in listOf("idea", "server")) {
      val record: DevDistPluginPlanRecord = records.getValue(key(product))
      val labels: DevDistPluginExecutionGraphLabels = files.executionGraphLabels(key(product), record)
      assertThat(labels.projection).isEqualTo("$planPackage:$plugin.dev-plan.json")
      assertThat(labels.planClass).isEmpty()
      files.checkExecutionGraph(key(product), record, labels)
    }
  }

  @Test
  fun `a product whose plan text differs writes a plan file of its own and the baseline keeps the plain name`() {
    val records: Map<DevDistPluginPlanKey, DevDistPluginPlanRecord> = records(serverDestination = "lib/x-server.jar")
    val ideaRecord: DevDistPluginPlanRecord = records.getValue(key("idea"))
    val serverRecord: DevDistPluginPlanRecord = records.getValue(key("server"))

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))

    assertThat(files.files.keys).containsExactly("$planDirectory/$plugin.dev-plan.json", "$planDirectory/$plugin.server.dev-plan.json")
    assertThat(files.files.getValue("$planDirectory/$plugin.dev-plan.json")).contains("lib/x.jar")
    assertThat(files.files.getValue("$planDirectory/$plugin.server.dev-plan.json")).contains("lib/x-server.jar")
    val idea: DevDistPluginExecutionGraphLabels = files.executionGraphLabels(key("idea"), ideaRecord)
    assertThat(idea.projection).isEqualTo("$planPackage:$plugin.dev-plan.json")
    assertThat(idea.planClass).isEmpty()
    val server: DevDistPluginExecutionGraphLabels = files.executionGraphLabels(key("server"), serverRecord)
    assertThat(server.projection).isEqualTo("$planPackage:$plugin.server.dev-plan.json")
    assertThat(server.planClass).isEqualTo("server")
    files.checkExecutionGraph(key("idea"), ideaRecord, idea)
    files.checkExecutionGraph(key("server"), serverRecord, server)

    // The product order decides the baseline, and the sorted product names do not.
    val reversed = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("server", "idea"))
    assertThat(reversed.files.keys).containsExactly("$planDirectory/$plugin.dev-plan.json", "$planDirectory/$plugin.idea.dev-plan.json")
    assertThat(reversed.executionGraphLabels(key("idea"), ideaRecord).planClass).isEqualTo("idea")
  }

  @Test
  fun `two products whose plan text differs from the baseline alike share one plan file named after the first of them`() {
    val records = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>()
    records.put(key("idea"), record("lib/x.jar"))
    records.put(key("server"), record("lib/x-client.jar"))
    records.put(key("client"), record("lib/x-client.jar"))

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server", "client"))

    assertThat(files.files.keys).containsExactly("$planDirectory/$plugin.dev-plan.json", "$planDirectory/$plugin.server.dev-plan.json")
    for (product in listOf("server", "client")) {
      val record: DevDistPluginPlanRecord = records.getValue(key(product))
      val labels: DevDistPluginExecutionGraphLabels = files.executionGraphLabels(key(product), record)
      assertThat(labels.projection).isEqualTo("$planPackage:$plugin.server.dev-plan.json")
      assertThat(labels.planClass).isEqualTo("server")
      files.checkExecutionGraph(key(product), record, labels)
    }
  }

  @Test
  fun `the plan file of a community plugin's non-baseline text sits in its cross-half package and is not exported`() {
    val records = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>()
    records.put(key("idea", plugin = communityPlugin), record("lib/c.jar", plugin = communityPlugin))
    records.put(key("server", plugin = communityPlugin), record("lib/c-server.jar", plugin = communityPlugin))

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))

    val crossHalf = "build/dev-dist-descriptors/$communityPlugin"
    assertThat(files.files.keys).containsExactlyInAnyOrder("community/plugins/c/$communityPlugin.dev-plan.json", "$crossHalf/$communityPlugin.server.dev-plan.json")
    assertThat(files.exportedFiles.getValue(communityPlugin)).containsExactly("$communityPlugin.dev-plan.json")
    val serverRecord: DevDistPluginPlanRecord = records.getValue(key("server", plugin = communityPlugin))
    val server: DevDistPluginExecutionGraphLabels = files.executionGraphLabels(key("server", plugin = communityPlugin), serverRecord)
    assertThat(server.projection).isEqualTo("//$crossHalf:$communityPlugin.server.dev-plan.json")
    assertThat(files.planHome(key("server", plugin = communityPlugin)).packageLabel).isEqualTo("//$crossHalf")
    files.checkExecutionGraph(key("server", plugin = communityPlugin), serverRecord, server)
  }

  @Test
  fun `a second product whose platform records fold to the baseline's body binds to the baseline's folded file`() {
    val records = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>()
    addPlatformRecords(records, "idea") { listOf("lib/x.jar") }
    addPlatformRecords(records, "server") { listOf("lib/x.jar") }

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))

    assertThat(files.files.keys).containsExactly("$planDirectory/$plugin.dev-plan.json")
    assertThat(files.files.getValue("$planDirectory/$plugin.dev-plan.json")).contains("\"{platform}\"").contains("\"{platform:layoutSignature}\"")
    for (product in listOf("idea", "server")) {
      for ((_, labels) in platformLabels(files, records, product)) {
        assertThat(labels.projection).isEqualTo("$planPackage:$plugin.dev-plan.json")
        assertThat(labels.planClass).isEmpty()
        assertThat(labels.platformValues.keys).containsExactly("layoutSignature")
      }
    }
  }

  @Test
  fun `a second product whose platform records fold to another body writes a folded file of its own`() {
    val records = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>()
    addPlatformRecords(records, "idea") { listOf("lib/x.jar") }
    addPlatformRecords(records, "server") { listOf("lib/x-server.jar") }

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))

    assertThat(files.files.keys).containsExactly("$planDirectory/$plugin.dev-plan.json", "$planDirectory/$plugin.server.dev-plan.json")
    assertThat(files.files.getValue("$planDirectory/$plugin.dev-plan.json")).contains("lib/x.jar")
    assertThat(files.files.getValue("$planDirectory/$plugin.server.dev-plan.json")).contains("lib/x-server.jar")
    for ((_, labels) in platformLabels(files, records, "idea")) {
      assertThat(labels.projection).isEqualTo("$planPackage:$plugin.dev-plan.json")
      assertThat(labels.planClass).isEmpty()
      assertThat(labels.platformValues.keys).containsExactly("layoutSignature")
    }
    for ((_, labels) in platformLabels(files, records, "server")) {
      assertThat(labels.projection).isEqualTo("$planPackage:$plugin.server.dev-plan.json")
      assertThat(labels.planClass).isEqualTo("server")
      assertThat(labels.platformValues.keys).containsExactly("layoutSignature")
    }
  }

  @Test
  fun `a refused fold keeps a file per platform, under the product name for a differing product and shared for an equal one`() {
    val records = LinkedHashMap<DevDistPluginPlanKey, DevDistPluginPlanRecord>()
    // The linux record packs one jar more, so the two shapes differ and the fold is refused.
    val ideaJars = { platform: String -> if (platform == "linux_x64") listOf("lib/x.jar", "lib/x-linux.jar") else listOf("lib/x.jar") }
    addPlatformRecords(records, "idea", ideaJars)
    addPlatformRecords(records, "server") { platform -> ideaJars(platform).map { it.replace("x.jar", "x-server.jar") } }
    addPlatformRecords(records, "other", ideaJars)

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server", "other"))

    assertThat(files.files.keys).containsExactly(
      "$planDirectory/$plugin.darwin_aarch64.dev-plan.json",
      "$planDirectory/$plugin.linux_x64.dev-plan.json",
      "$planDirectory/$plugin.server.darwin_aarch64.dev-plan.json",
      "$planDirectory/$plugin.server.linux_x64.dev-plan.json",
    )
    for (product in listOf("idea", "other")) {
      for ((platform, labels) in platformLabels(files, records, product)) {
        assertThat(labels.projection).isEqualTo("$planPackage:$plugin.$platform.dev-plan.json")
        assertThat(labels.planClass).isEmpty()
        assertThat(labels.platformValues).isEmpty()
      }
    }
    for ((platform, labels) in platformLabels(files, records, "server")) {
      assertThat(labels.projection).isEqualTo("$planPackage:$plugin.server.$platform.dev-plan.json")
      assertThat(labels.planClass).isEqualTo("server")
      assertThat(labels.platformValues).isEmpty()
    }
  }

  @Test
  fun `an ultimate plugin keeps its plan file in its own package and exports nothing`() {
    val files = collectDevDistPluginPlanFiles(dir, records(serverDestination = "lib/x.jar"), index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))

    val home = files.home(plugin)
    assertThat(home.directory).isEqualTo(planDirectory)
    assertThat(home.packageLabel).isEqualTo(planPackage)
    assertThat(home.callIsCrossHalf).isFalse()
    assertThat(home.exportsPlanFiles).isFalse()
    assertThat(home.isModulePackage).isTrue()
    assertThat(files.homes.keys).containsExactly(plugin)
    assertThat(files.exportedFiles).isEmpty()
  }

  @Test
  fun `a community plugin whose plan names community labels only keeps its plan file in its community package and exports it`() {
    val records = neutralRecords(communityPlugin, library = "@lib//:community-lib")

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea"))

    val home = files.home(communityPlugin)
    assertThat(home.directory).isEqualTo("community/plugins/c")
    assertThat(home.packageLabel).isEqualTo("@community//plugins/c")
    assertThat(home.callIsCrossHalf).isTrue()
    assertThat(home.exportsPlanFiles).isTrue()
    assertThat(home.isModulePackage).isTrue()
    assertThat(files.files.keys).containsExactly("community/plugins/c/$communityPlugin.dev-plan.json")
    assertThat(files.exportedFiles).containsExactly(entry(communityPlugin, listOf("$communityPlugin.dev-plan.json")))
    val record = records.getValue(key("idea", plugin = communityPlugin))
    val labels = files.executionGraphLabels(key("idea", plugin = communityPlugin), record)
    assertThat(labels.projection).isEqualTo("@community//plugins/c:$communityPlugin.dev-plan.json")
    files.checkExecutionGraph(key("idea", plugin = communityPlugin), record, labels)
  }

  @Test
  fun `a community plugin whose plan names another repository gets the cross-half package and no export`() {
    val records = neutralRecords(communityPlugin, library = "@ultimate_lib//:profiler")

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea"))

    val home = files.home(communityPlugin)
    assertThat(home.directory).isEqualTo("build/dev-dist-descriptors/$communityPlugin")
    assertThat(home.packageLabel).isEqualTo("//build/dev-dist-descriptors/$communityPlugin")
    assertThat(home.callIsCrossHalf).isTrue()
    assertThat(home.exportsPlanFiles).isFalse()
    assertThat(home.isModulePackage).isFalse()
    assertThat(files.files.keys).containsExactly("build/dev-dist-descriptors/$communityPlugin/$communityPlugin.dev-plan.json")
    assertThat(files.files.values.single()).contains("@ultimate_lib//:profiler")
    assertThat(files.exportedFiles).isEmpty()
    val record = records.getValue(key("idea", plugin = communityPlugin))
    val labels = files.executionGraphLabels(key("idea", plugin = communityPlugin), record)
    assertThat(labels.projection).isEqualTo("//build/dev-dist-descriptors/$communityPlugin:$communityPlugin.dev-plan.json")
    files.checkExecutionGraph(key("idea", plugin = communityPlugin), record, labels)
  }

  /**
   * The plans of an ultimate pass whose own package of [communityPlugin] holds the plan file of [records]. The own
   * section states [sectionCall], and it exports the plan file when [exported] is true.
   */
  private fun ownPackagePlans(
    records: Map<DevDistPluginPlanKey, DevDistPluginPlanRecord>,
    sectionCall: String?,
    exported: Boolean,
  ): DevDistOwnPackagePlans {
    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea"))
    val calls = DevDistPluginCallRendering(
      sectionText = sectionCall,
      crossHalfPath = crossHalfPackagePath(communityPlugin, product = null),
      crossHalfText = if (exported) "dev_dist_complex_plugin()\n" else null,
      exportsPlanFiles = exported,
    )
    return DevDistOwnPackagePlans.of(files, DevDistPluginExecutionRendering(calls = mapOf(communityPlugin to calls), components = ""))
  }

  /** The plan files of the community pass over [records], which reuses a plan file of [ownPackagePlans]. */
  private fun collectCommunityPass(
    records: Map<DevDistPluginPlanKey, DevDistPluginPlanRecord>,
    ownPackagePlans: DevDistOwnPackagePlans,
  ): DevDistPluginPlanFiles {
    val root = DevDistGenerationRoot.community(dir)
    val communityIndex = DevDistBazelIndex(targets = index.targets, projectRoot = dir, planPackageIsCommunity = true)
    return collectDevDistPluginPlanFiles(
      projectRoot = root.outputRoot,
      records = records,
      index = communityIndex,
      half = CommunityDevDistHalf,
      productOrder = listOf("idea"),
      writtenText = root::respellQuotedLabels,
      ownHome = { plugin, writtenFiles -> ownPackagePlans.reusableHome(plugin, writtenFiles, root) },
    )
  }

  private fun relativePaths(files: DevDistPluginPlanFiles, changeType: FileChangeType): List<String> {
    return files.updates.getDiffs().filter { it.changeType == changeType }.map { dir.relativize(it.path).toString().replace('\\', '/') }
  }

  @Test
  fun `the community pass reuses an identical plan file of the own package and writes a differing one in its generated package`() {
    val ultimateRecords = neutralRecords(communityPlugin, library = "@lib//:community-lib")
    val own = ownPackagePlans(ultimateRecords, sectionCall = "dev_dist_complex_plugin()\n", exported = false)
    // The ultimate pass wrote the own plan file, and an earlier community pass wrote a copy into the generated package.
    val ownFile = "community/plugins/c/$communityPlugin.dev-plan.json"
    val copy = "community/build/dev-dist-descriptors/$communityPlugin/$communityPlugin.dev-plan.json"
    for (path in listOf(ownFile, copy)) {
      Files.writeString(Files.createDirectories(dir.resolve(path).parent).resolve(path.substringAfterLast('/')), "{}\n")
    }

    val communityRecords = neutralRecords(communityPlugin, library = "@lib//:community-lib")
    val identical = collectCommunityPass(communityRecords, own)

    assertThat(identical.reusedHomes).containsExactly(communityPlugin)
    val home = identical.home(communityPlugin)
    assertThat(home.directory).isEqualTo("plugins/c")
    assertThat(home.packageLabel).isEqualTo("@community//plugins/c")
    assertThat(home.exportsPlanFiles).isTrue()
    // The pass neither writes nor sweeps the own package, and it deletes the copy.
    assertThat(relativePaths(identical, FileChangeType.CREATE)).isEmpty()
    assertThat(relativePaths(identical, FileChangeType.MODIFY)).isEmpty()
    assertThat(relativePaths(identical, FileChangeType.DELETE)).containsExactly(copy)
    // A call reads the plan file of the own package.
    val key = key("idea", plugin = communityPlugin)
    val record = communityRecords.getValue(key)
    val labels = identical.executionGraphLabels(key, record)
    assertThat(labels.projection).isEqualTo("@community//plugins/c:$communityPlugin.dev-plan.json")
    identical.checkExecutionGraph(key, record, labels)

    val differing = collectCommunityPass(neutralRecords(communityPlugin, library = "@lib//:other-lib"), own)

    assertThat(differing.reusedHomes).isEmpty()
    assertThat(differing.home(communityPlugin).directory).isEqualTo("build/dev-dist-descriptors/$communityPlugin")
    assertThat(relativePaths(differing, FileChangeType.MODIFY)).containsExactly(copy)
    assertThat(relativePaths(differing, FileChangeType.DELETE)).isEmpty()
  }

  @Test
  fun `a plan file that differs from the own one only in the label spelling is not reused`() {
    val records = neutralRecords(communityPlugin, library = "@community//libraries/c:c")
    val own = ownPackagePlans(records, sectionCall = null, exported = true)

    val files = collectCommunityPass(records, own)

    assertThat(files.reusedHomes).isEmpty()
    assertThat(files.files.values.single()).contains("\"@community//libraries/c:c\"")
    assertThat(relativePaths(files, FileChangeType.CREATE))
      .containsExactly("community/build/dev-dist-descriptors/$communityPlugin/$communityPlugin.dev-plan.json")
  }

  @Test
  fun `the own package serves the calls of the community pass only when it states the same call or exports the plan file`() {
    val records = neutralRecords(communityPlugin, library = "@lib//:community-lib")
    val fileNames = listOf("$communityPlugin.dev-plan.json")
    fun calls(sectionText: String?, exportsPlanFiles: Boolean) = DevDistPluginCallRendering(
      sectionText = sectionText,
      crossHalfPath = crossHalfPackagePath(communityPlugin, product = null),
      crossHalfText = if (exportsPlanFiles) "dev_dist_complex_plugin()\n" else null,
      exportsPlanFiles = exportsPlanFiles,
    )

    val inSection = ownPackagePlans(records, sectionCall = "dev_dist_complex_plugin(a)\n", exported = false)
    assertThat(inSection.acceptsCalls(communityPlugin, calls("dev_dist_complex_plugin(a)\n", exportsPlanFiles = false), fileNames)).isTrue()
    assertThat(inSection.acceptsCalls(communityPlugin, calls("dev_dist_complex_plugin(b)\n", exportsPlanFiles = false), fileNames)).isFalse()
    // The own section holds the call beside the plan file, so it exports nothing that a call of the generated package reads.
    assertThat(inSection.acceptsCalls(communityPlugin, calls(sectionText = null, exportsPlanFiles = true), fileNames)).isFalse()

    val exported = ownPackagePlans(records, sectionCall = null, exported = true)
    assertThat(exported.acceptsCalls(communityPlugin, calls(sectionText = null, exportsPlanFiles = true), fileNames)).isTrue()
    assertThat(exported.acceptsCalls(communityPlugin, calls("dev_dist_complex_plugin(a)\n", exportsPlanFiles = false), fileNames)).isFalse()
  }

  @Test
  fun `a plugin the index does not place has no plan home`() {
    val records = neutralRecords("intellij.unplaced")

    assertThatThrownBy { collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("Plugin 'intellij.unplaced' has no Bazel package, so its plan files have no home")
  }

  @Test
  fun `a plugin whose package has no BUILD file fails the run`() {
    val records = neutralRecords("intellij.noBuildFile")

    assertThatThrownBy { collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("Plugin 'intellij.noBuildFile' has no plugins/no-build-file/BUILD.bazel, so its plan files have no package")
  }

  @Test
  fun `the sweep deletes every plan file the run did not emit, in every module package and every cross-half package`() {
    val stale = listOf(
      "plugins/x/intellij.x.linux_x64.dev-plan.json",
      "community/plugins/c/intellij.c.dev-plan.json",
      "build/dev-dist-descriptors/intellij.gone/intellij.gone.dev-plan.json",
    )
    val kept = listOf("plugins/x/notes.json", "build/dev-dist-descriptors/intellij.gone/BUILD.bazel")
    for (path in stale + kept) {
      val file = dir.resolve(path)
      Files.createDirectories(file.parent)
      Files.writeString(file, "{}\n")
    }
    val records = records(serverDestination = "lib/x.jar") + neutralRecords(communityPlugin, library = "@ultimate_lib//:profiler")

    val files = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))

    val diffs = files.updates.getDiffs()
    val deleted = diffs.filter { it.changeType == FileChangeType.DELETE }.map { dir.relativize(it.path).toString().replace('\\', '/') }
    assertThat(deleted).containsExactlyInAnyOrderElementsOf(stale)
    val created = diffs.filter { it.changeType == FileChangeType.CREATE }.map { dir.relativize(it.path).toString().replace('\\', '/') }
    assertThat(created).containsExactly("build/dev-dist-descriptors/intellij.c/intellij.c.dev-plan.json", "plugins/x/intellij.x.dev-plan.json")
    files.updates.commit()
    for (path in stale) {
      assertThat(dir.resolve(path)).doesNotExist()
    }
    for (path in kept) {
      assertThat(dir.resolve(path)).exists()
    }
    for (path in created) {
      assertThat(dir.resolve(path)).isRegularFile()
    }
    // A second run over the same records changes nothing.
    val again = collectDevDistPluginPlanFiles(dir, records, index, CommunityDevDistHalf, productOrder = listOf("idea", "server"))
    assertThat(again.updates.getDiffs()).isEmpty()
  }
}

/**
 * An index that places each module of [modules] at its production jar label, over [checkout]. The label tells the half
 * and the package, as `build/bazel-targets.json` does.
 */
internal fun syntheticIndex(checkout: Path, vararg modules: Pair<String, String>): DevDistBazelIndex {
  return DevDistBazelIndex(
    targets = BazelTargetsInfo.TargetsFile(
      modules = modules.associate { (module, label) ->
        module to BazelTargetsInfo.TargetsFileModuleDescription(
          productionTargets = listOf(label),
          productionJars = emptyList(),
          testTargets = emptyList(),
          testJars = emptyList(),
          exports = emptyList(),
          moduleLibraries = emptyMap(),
        )
      },
      projectLibraries = emptyMap(),
      pluginDistributionTargets = emptyMap(),
    ),
    projectRoot = checkout,
  )
}
