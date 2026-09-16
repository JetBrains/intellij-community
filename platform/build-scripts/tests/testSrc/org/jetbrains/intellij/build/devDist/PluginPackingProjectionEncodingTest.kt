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
      JarSourceRecipe(input = "@lib//:demo", kind = "library", filter = "library-v1", expansion = listOf("@lib//:org.demo/demo-1.0.jar")),
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
    reusableArtifacts = listOf(
      ReusableJarArtifact(label = "//demo/core:core_content_module_jar.production.jar", recipe = moduleJarRecipe("intellij.demo.core")),
      ReusableJarArtifact(label = "//demo/merged:merged_content_module_jar.production.jar", recipe = mergedRecipe),
    ),
  )

  @Test
  fun `a module jar states only its module, and every asset decodes to what was encoded`() {
    val text = json.encodeToString(PluginPackingProjection.serializer(), projection)

    assertThat(text).contains("""{"module":"intellij.demo.core"}""")
    assertThat(text).doesNotContain("lib/modules/intellij.demo.core.jar")
    assertThat(text).contains("""{"label":"//demo/core:core_content_module_jar.production.jar","module":"intellij.demo.core"}""")
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
                  "recipe":{"sources":[{"input":"intellij.demo.core","kind":"module","filter":"module-v1"}],"writer":{"mergeEntities":true}}}],
       "reusableArtifacts":[{"label":"//demo/core:core_content_module_jar.production.jar",
                  "recipe":{"sources":[{"input":"intellij.demo.core","kind":"module","filter":"module-v1"}],"writer":{"mergeEntities":true}}}]}
    """.trimIndent()

    val decoded = json.decodeFromString(PluginPackingProjection.serializer(), full)

    assertThat(decoded.assets).containsExactly(moduleJarAsset("intellij.demo.core"))
    assertThat(decoded.reusableArtifacts).containsExactly(
      ReusableJarArtifact(label = "//demo/core:core_content_module_jar.production.jar", recipe = moduleJarRecipe("intellij.demo.core")),
    )
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

  @Test
  fun `explicit defaults beside the module are accepted`() {
    val text = """{"version":1,"plugin":"p","variant":"","layoutSignature":"l",
      "assets":[{"module":"intellij.demo.core","destination":null,"inputs":null,"recipe":null,"mode":420,"symlinkTarget":null}]}"""

    assertThat(json.decodeFromString(PluginPackingProjection.serializer(), text).assets).containsExactly(moduleJarAsset("intellij.demo.core"))
  }
}
