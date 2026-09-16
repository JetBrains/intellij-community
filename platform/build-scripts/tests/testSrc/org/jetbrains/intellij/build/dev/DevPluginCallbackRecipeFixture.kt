@file:Suppress("DEPRECATION", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.FileSource
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.buildJar
import org.jetbrains.intellij.build.classPath.generatePluginClassPathFromOrderedAssets
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.PreparedSourceManifestRecipe
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.jetbrains.intellij.build.impl.LibraryEntriesLayoutPatcher
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.commonModuleExcludes
import org.jetbrains.intellij.build.impl.createModuleSourcesNamesFilter
import java.lang.reflect.Proxy
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Emits serialized callback recipes and independent original outputs for the Go parity tests. */
object DevPluginCallbackRecipeFixture {
  private val json = Json { encodeDefaults = true }
  private const val MAIN = "fixture.callback.main"
  private const val OTHER = "fixture.callback.other"
  private const val OWNER = "fixture.callback.library.owner"
  private const val DESCRIPTOR = "<idea-plugin><id>fixture.callback</id><name>Callback fixture</name></idea-plugin>"

  @JvmStatic
  fun main(args: Array<String>) {
    System.setProperty("intellij.build.snap.docker.image", "callback-recipe-fixture")
    val options = LinkedHashMap<String, String>()
    for (argument in args) {
      require(argument.startsWith("--") && '=' in argument) { "Expected --name=value" }
      require(options.put(argument.substringBefore('='), argument.substringAfter('=')) == null) { "Duplicate fixture option" }
    }
    val root = Files.createDirectories(Path.of(options.remove("--root") ?: error("Missing --root")).toAbsolutePath().normalize())
    val fixture = options.remove("--fixture")
    require(options.isEmpty()) { "Unknown fixture options: ${options.keys}" }
    require(fixture == "library") { "Unknown callback fixture '$fixture'" }
    library(root)
  }

  private fun library(root: Path) {
    checkSeparateDescriptorRecipe()
    val first = root.resolve("preparation-only/first.jar")
    val second = root.resolve("preparation-only/second.jar")
    for ((index, path) in listOf(first, second).withIndex()) {
      val name = if (index == 0) "first" else "second"
      archive(
        path, "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\nCreated-By: $name\n",
        "drop/$name.txt" to "excluded", "keep.txt" to name, "keep/$name.txt" to name,
        "META-INF/listOfEntities.txt" to "$name.Entity\n",
      )
    }
    val archive = root.resolve("raw/library.jar")
    archive(
      archive, "extensions/z.txt" to "last", "unmatched.txt" to "raw only", "extensions/a.txt" to "first",
      "other/value.txt" to "other", "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\nCreated-By: library\n",
    )
    val descriptor = writeFile(root.resolve("preparation-only/plugin.xml"), DESCRIPTOR)
    val seedFile = writeFile(root.resolve("preparation-only/seed.txt"), "seed")
    val layout = PluginLayout.plugin(MAIN) { it.mainJarName = "module.jar" }
    (layout.includedModules as MutableCollection<ModuleItem>).clear()
    layout.withModules(sequenceOf(
      ModuleItem(OTHER, "merged.jar", "ordered archive roots", listOf("first", "second"), true),
      ModuleItem(MAIN, "module.jar", null),
      ModuleItem(OTHER, "sibling/other.jar", "repeated module contribution"),
    ))
    layout.excludeFromModule(OTHER, "drop/**")
    val callbacks = listOf(
      LibraryEntriesLayoutPatcher("library", OWNER, "other/", OTHER),
      LibraryEntriesLayoutPatcher("library", OWNER, "extensions/", MAIN),
      LibraryEntriesLayoutPatcher("library", OWNER, "extensions/", MAIN),
    )
    callbacks.forEach(layout::withPatch)
    val patchers = callbacks.map { callback ->
      DevPluginLayoutPatcherInputs(
        callback, listOf(DevPluginLayoutLibraryInput("library", OWNER, listOf(DevPluginReference("library")))),
        listOf(DevPluginLayoutSourceInput(OTHER, "absent.xml", null)),
      )
    }
    val filters = listOf(DevPluginLayoutFilter(
      OTHER, listOf(DevPluginReference("first"), DevPluginReference("second")), listOf("filtered-first", "filtered-second"), "keep",
    ))
    val outputs = listOf(DevPluginLayoutPatchOutput(OTHER, "patched-other"), DevPluginLayoutPatchOutput(MAIN, "patched-main"))
    val seeds = listOf(seed(MAIN, PLUGIN_XML_RELATIVE_PATH, "descriptor", DESCRIPTOR), seed(OTHER, "seed.txt", "seed", "seed"))
    val adapter = DevPluginLayoutPreparationAdapter(layout, filters, patchers, outputs, seeds, "library-layout")
    fun prepared(id: String): JarSourceRecipe {
      val manifest = adapter.facts.preparedSourceManifests.getValue(id)
      return JarSourceRecipe(id, "prepared", "prepared", preparedManifest = PreparedSourceManifestRecipe(
        originalMeaningfulSourceCount = manifest.originalMeaningfulSourceCount, sourceManifestPolicies = manifest.sourceManifestPolicies,
      ))
    }
    fun jar(name: String, vararg inputs: String): PluginPackingAsset {
      return PluginPackingAsset("lib/$name.jar", inputs.distinct(), CanonicalJarRecipe(inputs.map(::prepared), JarWriterRecipe(mergeEntities = true)))
    }
    val assets = listOf(
      jar("merged", "patched-other", "patched-main", "filtered-first", "filtered-second", "filtered-first"),
      jar("module", "patched-main", "filtered-first"),
      jar("sibling/other", "patched-other", "filtered-first", "filtered-second").copy(classPath = false),
      PluginPackingAsset("lib/raw.jar", listOf("library"),
                         CanonicalJarRecipe(listOf(JarSourceRecipe("library", "archive", "all")), JarWriterRecipe(mergeEntities = true))),
      jar("single", "filtered-first"),
    )
    val plan = planPluginPacking(MAIN, "library", assets, adapter.preparations(), emptyList(), emptyList())
    val configuration = snapshotDevPluginLibraryLayoutRecipe(layout, filters, patchers, outputs, seeds, plan)
    val catalogue = DevPluginArtifactCatalogue(artifacts = listOf(
      DevPluginArtifact("library", "file", "raw/library.jar"),
      DevPluginArtifact("first", "file", "preparation-only/first.jar"),
      DevPluginArtifact("second", "file", "preparation-only/second.jar"),
      DevPluginArtifact("descriptor", "file", "preparation-only/plugin.xml"),
      DevPluginArtifact("seed", "file", "preparation-only/seed.txt"),
    ))
    val derivation = writeContracts(root, plan, catalogue, adapter.preparations(), configuration.toDevPluginPreparationOperations())
    val originalCatalogue = absoluteCatalogue(root, catalogue)
    prepareDevPlugin(
      plan, plan.layoutSignature, derivation.inputs, originalCatalogue, DESCRIPTOR.toByteArray(), Path.of("plugin"), root.resolve("reference-prepared"),
      adapter.compileActions(layout, plan, originalCatalogue), catalogueOutputDirectory = Path.of("reference-prepared"),
    )

    val patcher = ModuleOutputPatcher()
    for ((seed, path) in seeds.zip(listOf(descriptor, seedFile))) {
      patcher.patchModuleOutputWithFile(seed.moduleName, seed.entry, FileSource(seed.entry, seed.size, seed.hash, path))
    }
    val context = originalLibraryContext(archive)
    for (callback in callbacks) callback(patcher, PlatformLayout(), context)
    check(patcher.getPatchedSources(MAIN).keys.toList() == listOf(PLUGIN_XML_RELATIVE_PATH, "extensions/z.txt", "extensions/a.txt"))
    check(patcher.getPatchedSources(OTHER).keys.toList() == listOf("seed.txt", "other/value.txt"))
    val excludes = commonModuleExcludes + layout.moduleExcludes.getValue(OTHER).map { FileSystems.getDefault().getPathMatcher("glob:$it") }
    val include = createModuleSourcesNamesFilter(excludes)
    fun filtered(path: Path): ZipSource = ZipSource(path, distributionFileEntryProducer = null, moduleName = OTHER, filter = include)
    val otherPatches = patcher.getPatchedSources(OTHER).values.toList()
    val mainPatches = patcher.getPatchedSources(MAIN).values.toList()
    val reference = root.resolve("reference/lib")
    buildJar(reference.resolve("merged.jar"), buildList {
      addAll(otherPatches)
      addAll(mainPatches)
      add(filtered(first))
      add(filtered(second))
      add(filtered(first))
    })
    buildJar(reference.resolve("module.jar"), mainPatches + filtered(first))
    buildJar(reference.resolve("sibling/other.jar"), otherPatches + listOf(filtered(first), filtered(second)))
    buildJar(reference.resolve("raw.jar"), listOf(zipSource(archive)))
    buildJar(reference.resolve("single.jar"), listOf(filtered(first)))
    writeClassPath(root, listOf("lib/merged.jar", "lib/module.jar", "lib/raw.jar", "lib/single.jar"))
  }

  private fun checkSeparateDescriptorRecipe() {
    val layout = PluginLayout.plugin(MAIN) { it.mainJarName = "module.jar" }
    val callback = LibraryEntriesLayoutPatcher("library", OWNER, "META-INF/extensions/", MAIN)
    layout.withPatch(callback)
    val patchers = listOf(DevPluginLayoutPatcherInputs(
      callback = callback,
      libraries = listOf(DevPluginLayoutLibraryInput("library", OWNER, listOf(DevPluginReference("library")))),
    ))
    val outputs = listOf(DevPluginLayoutPatchOutput(MAIN, "separate-descriptor-patches"))
    val adapter = DevPluginLayoutPreparationAdapter(layout, emptyList(), patchers, outputs, emptyList(), "separate-descriptor-layout")
    val manifest = adapter.facts.preparedSourceManifests.getValue(outputs.single().output)
    val sources = listOf(
      JarSourceRecipe("descriptor", "file", "none", PLUGIN_XML_RELATIVE_PATH, options = listOf("patch")),
      JarSourceRecipe(outputs.single().output, "prepared", "prepared", preparedManifest = PreparedSourceManifestRecipe(
        originalMeaningfulSourceCount = manifest.originalMeaningfulSourceCount,
        sourceManifestPolicies = manifest.sourceManifestPolicies,
      )),
    )
    val asset = PluginPackingAsset(
      destination = "lib/module.jar",
      inputs = sources.map { it.input },
      recipe = CanonicalJarRecipe(sources, JarWriterRecipe(mergeEntities = true)),
    )
    val plan = planPluginPacking(MAIN, "separate-descriptor", listOf(asset), adapter.preparations(), emptyList(), emptyList())
    val configuration = snapshotDevPluginLibraryLayoutRecipe(
      layout, emptyList(), patchers, outputs, emptyList(), plan, idPrefix = "separate-descriptor-layout",
    )
    val catalogue = DevPluginArtifactCatalogue(artifacts = listOf(
      DevPluginArtifact("descriptor", "file", "plugin.xml"),
      DevPluginArtifact("library", "file", "library.jar"),
    ))
    val operations = configuration.toDevPluginPreparationOperations()
    validateDevPluginOperations(plan, catalogue.toPlanCatalogue(), operations)
    deriveDevPluginInputs(plan, catalogue.toPlanCatalogue(), operations)
    check(configuration.seeds.isEmpty() && configuration.callbacks.single().prefix == "META-INF/extensions/")
    check(adapter.facts.modulePatches.getValue(MAIN).map { it.input } == listOf(outputs.single().output))
    check(plan.assets.single().asset.recipe!!.sources.map { it.input } == listOf("descriptor", outputs.single().output))
  }

  private fun writeContracts(
    root: Path,
    plan: PluginPackingPlan,
    catalogue: DevPluginArtifactCatalogue,
    originalPreparations: List<PluginPackingPreparation>,
    operations: List<DevPluginPreparationOperation>,
  ): DevPluginInputDerivation {
    val projection = PluginPackingProjection(
      plugin = plan.plugin, variant = plan.variant, layoutSignature = plan.layoutSignature,
      assets = plan.assets.map { it.asset }, preparations = originalPreparations, operations = operations,
    )
    validateDevPluginOperations(plan, catalogue.toPlanCatalogue(), operations)
    val derivation = deriveDevPluginInputs(plan, catalogue.toPlanCatalogue(), operations)
    Files.writeString(root.resolve("projection.json"), json.encodeToString(projection))
    Files.writeString(root.resolve("catalogue.json"), json.encodeToString(catalogue))
    Files.writeString(root.resolve("remainder-inputs.json"), json.encodeToString(derivation.remainderInputs))
    Files.writeString(root.resolve("descriptor.xml"), DESCRIPTOR)
    return derivation
  }

  private fun originalLibraryContext(archive: Path): BuildContext {
    val provider = Proxy.newProxyInstance(ModuleOutputProvider::class.java.classLoader, arrayOf(ModuleOutputProvider::class.java)) { _, method, arguments ->
      check(method.name == "findLibraryRoots" && arguments!!.toList() == listOf("library", OWNER)) { "Unexpected original library lookup: $method" }
      listOf(archive)
    } as ModuleOutputProvider
    return Proxy.newProxyInstance(BuildContext::class.java.classLoader, arrayOf(BuildContext::class.java)) { _, method, _ ->
      check(method.name == "getOutputProvider") { "Unexpected original context lookup: $method" }
      provider
    } as BuildContext
  }

  private fun seed(module: String, entry: String, input: String, content: String): DevPluginLayoutPatchSeed {
    val bytes = content.toByteArray()
    return DevPluginLayoutPatchSeed(module, entry, DevPluginReference(input), bytes.size, Hashing.xxh3_64().hashBytesToLong(bytes))
  }

  private fun absoluteCatalogue(root: Path, catalogue: DevPluginArtifactCatalogue): DevPluginArtifactCatalogue {
    return catalogue.copy(artifacts = catalogue.artifacts.map { it.copy(root = root.resolve(it.root).toString()) })
  }

  private fun writeClassPath(root: Path, files: List<String>) {
    val plugin = Path.of("plugin")
    Files.write(root.resolve("reference-classpath.txt"), generatePluginClassPathFromOrderedAssets(
      plugin, files.map(plugin::resolve), DESCRIPTOR.toByteArray(),
    ))
  }

  private fun zipSource(path: Path): Source = ZipSource(path, distributionFileEntryProducer = null, moduleName = null, filter = { true })

  private fun archive(path: Path, vararg entries: Pair<String, String>) {
    Files.createDirectories(path.parent)
    ZipOutputStream(Files.newOutputStream(path)).use { output ->
      for ((name, content) in entries) {
        output.putNextEntry(ZipEntry(name))
        output.write(content.toByteArray())
        output.closeEntry()
      }
    }
  }

  private fun writeFile(path: Path, content: String, mode: Int = 420): Path {
    Files.createDirectories(path.parent)
    Files.writeString(path, content)
    Files.setPosixFilePermissions(path, permissions(mode))
    return path
  }

  private fun permissions(mode: Int): Set<PosixFilePermission> {
    return PosixFilePermission.entries.filterIndexedTo(HashSet()) { index, _ -> mode and (1 shl (8 - index)) != 0 }
  }
}
