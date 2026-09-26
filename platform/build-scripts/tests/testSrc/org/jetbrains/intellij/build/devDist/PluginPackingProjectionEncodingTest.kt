// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.dev.DevPluginPreparationOperation
import org.jetbrains.intellij.build.dev.DevPluginReference
import org.junit.jupiter.api.Test

/** The compact projection encoding round-trips, and the full form still decodes; see `PluginPackingProjectionEncoding.kt`. */
class PluginPackingProjectionEncodingTest {
  private val json = Json {
    encodeDefaults = false
    explicitNulls = false
  }

  private val mergedRecipe = CanonicalJarRecipe(
    sources = listOf(
      JarSourceRecipe(input = "intellij.demo.merged", kind = "module", filter = "module-v1"),
      JarSourceRecipe(input = "@lib//:demo", kind = "library", filter = "library-v1"),
    ),
    writer = JarWriterRecipe(mergeEntities = true),
  )

  private val projection = PluginPackingProjection(
    version = 2,
    plugin = "intellij.demo",
    variant = "",
    layoutSignature = "layout",
    assets = listOf(
      moduleJarAsset("intellij.demo.core"),
      PluginPackingAsset(destination = "lib/demo.jar", inputs = listOf("intellij.demo.merged", "@lib//:demo"), recipe = mergedRecipe),
      PluginPackingAsset(destination = "lib/renamed.jar", inputs = listOf("descriptor:idea:intellij.demo", "intellij.demo.ui"), recipe = moduleJarRecipe("intellij.demo.ui")),
      PluginPackingAsset(destination = "resources", inputs = listOf("module-resource:0:source"), kind = "tree", classPath = false),
    ),
  )

  @Test
  fun `a module jar states only its module, and every asset decodes to what was encoded`() {
    val text = json.encodeToString(PluginPackingProjection.serializer(), projection)

    assertThat(text).contains("""{"module":"intellij.demo.core"}""")
    assertThat(text).doesNotContain("lib/modules/intellij.demo.core.jar")
    // The merged jar repeats its sources as inputs, so it states none. The renamed jar has an input its recipe lacks.
    assertThat(text).contains("""{"destination":"lib/demo.jar","recipe":""")
    assertThat(text).contains("""{"destination":"lib/renamed.jar","inputs":["descriptor:idea:intellij.demo","intellij.demo.ui"]""")
    assertThat(json.decodeFromString(PluginPackingProjection.serializer(), text)).isEqualTo(projection)
  }

  @Test
  fun `the full form still decodes`() {
    val full = """
      {"version":2,"plugin":"intellij.demo","variant":"","layoutSignature":"layout",
       "assets":[{"destination":"lib/modules/intellij.demo.core.jar","inputs":["intellij.demo.core"],
                  "recipe":{"sources":[{"input":"intellij.demo.core","kind":"module","filter":"module-v1"}],"writer":{"mergeEntities":true}}}]}
    """.trimIndent()

    val decoded = json.decodeFromString(PluginPackingProjection.serializer(), full)

    assertThat(decoded.assets).containsExactly(moduleJarAsset("intellij.demo.core"))
  }

  /** The reuse decision is not in the file. A stale plan that still states it fails with the field name. */
  @Test
  fun `a plan with reusableArtifacts is refused`() {
    val stale = """{"version":1,"plugin":"p","variant":"","layoutSignature":"l","assets":[{"module":"intellij.demo.core"}],
      "reusableArtifacts":[{"label":"//demo/core:core_content_module_jar.production.jar","module":"intellij.demo.core"}]}"""

    assertThat(runCatching { Json.decodeFromString(PluginPackingProjection.serializer(), stale) }.exceptionOrNull())
      .isNotNull()
      .hasMessageContaining("reusableArtifacts")
  }

  /** A reused jar is a module asset of the plan; the chain names the module, and the plan resolves the match by recipe. */
  @Test
  fun `the plan marks the module asset of a reused jar as independent`() {
    val signed = projection.copy(layoutSignature = pluginPackingLayoutSignature(
      projection.plugin, projection.variant, projection.assets, projection.preparations, projection.preparationRoots,
    ))
    val plan = signed.plan(listOf(ReusableJarArtifact(module = "intellij.demo.core", recipe = moduleJarRecipe("intellij.demo.core"))))

    assertThat(plan.assets.map { it.artifact?.module }).containsExactly("intellij.demo.core", null, null, null)
    assertThat(plan.requiredInputs).doesNotContain("intellij.demo.core")
    assertThat(runCatching { signed.plan(listOf(ReusableJarArtifact(module = "intellij.demo.other", recipe = moduleJarRecipe("intellij.demo.other")))) }.exceptionOrNull())
      .hasMessageContaining("duplicate or unused reusable artifacts")
  }

  /** The plan file is written with these settings; the preparer decodes it with the default `Json`. */
  private val planJson = Json {
    encodeDefaults = false
    explicitNulls = true
  }

  @Test
  fun `the operations survive the plan file encoding and the preparer decoder`() {
    val operation = DevPluginPreparationOperation(
      id = "filter", input = DevPluginReference("raw"), output = "filtered", manifest = "drop", excludes = listOf("drop/**"),
    )
    val text = planJson.encodeToString(PluginPackingProjection.serializer(), projection.copy(operations = listOf(operation)))

    val decoded = Json.decodeFromString(PluginPackingProjection.serializer(), text)

    assertThat(decoded.operations).containsExactly(operation)
    assertThat(decoded.copy(operations = emptyList())).isEqualTo(projection)
  }

  /** The Go mirror of the layout signature pins the same constant in `kotlin_preparation_test.go`. */
  @Test
  fun `the layout signature of the filtered projection is what the Go mirror pins`() {
    val signature = pluginPackingLayoutSignature(
      plugin = "filtered-plugin",
      variant = "linux",
      assets = listOf(PluginPackingAsset(
        destination = "lib/main.jar",
        inputs = listOf("filtered"),
        recipe = CanonicalJarRecipe(sources = listOf(JarSourceRecipe("filtered", "prepared", "prepared")), writer = JarWriterRecipe(manifest = "drop")),
      )),
      preparations = listOf(PluginPackingPreparation(id = "filter", inputs = listOf("raw"), outputs = listOf("filtered"), modelSignature = "4j4kth710fglswfsh1vosdrg4")),
      preparationRoots = emptyList(),
    )

    assertThat(signature).isEqualTo("ardmbz5a2oe6vf6br6theud68")
  }

  @Test
  fun `explicit defaults beside the module are accepted`() {
    val text = """{"version":1,"plugin":"p","variant":"","layoutSignature":"l",
      "assets":[{"module":"intellij.demo.core","destination":null,"inputs":null,"recipe":null,"mode":420,"symlinkTarget":null}]}"""

    assertThat(json.decodeFromString(PluginPackingProjection.serializer(), text).assets).containsExactly(moduleJarAsset("intellij.demo.core"))
  }
}
