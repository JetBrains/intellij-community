// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

/*
 * The compact projection encoding.
 *
 * A module's own jar is the commonest asset by far: one `module-v1` source, the dev-distribution writer, the module as
 * the one input, and `lib/modules/<module>.jar` as the destination. The compact form states it as `{"module": "<m>"}`.
 * Any other asset leaves `inputs` out when the list repeats the recipe sources. The full form stays readable, and a
 * decoded projection equals the encoded one, so the layout signature, which hashes the objects, does not change.
 *
 * Only `PluginPackingProjection.assets` uses this codec. A library layout recipe embeds the same classes, and keeps
 * the full form, so the recipe text and every signature over it stay as they are.
 */

/** The recipe of a module's own jar. */
@ApiStatus.Internal
fun moduleJarRecipe(module: String): CanonicalJarRecipe {
  return CanonicalJarRecipe(
    sources = listOf(JarSourceRecipe(input = module, kind = "module", filter = "module-v1")),
    writer = JarWriterRecipe(mergeEntities = true),
  )
}

/** The asset of a module's own jar at its default destination. */
@ApiStatus.Internal
fun moduleJarAsset(module: String): PluginPackingAsset {
  return PluginPackingAsset(destination = "lib/modules/$module.jar", inputs = listOf(module), recipe = moduleJarRecipe(module))
}

/** The module whose own jar [recipe] builds, or null when the recipe is another one. */
private fun moduleOf(recipe: CanonicalJarRecipe?): String? {
  val module = recipe?.sources?.singleOrNull()?.input ?: return null
  return module.takeIf { recipe == moduleJarRecipe(it) }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private class CompactAsset(
  @JvmField val module: String? = null,
  @JvmField val destination: String? = null,
  @JvmField val inputs: List<String>? = null,
  @JvmField val recipe: CanonicalJarRecipe? = null,
  @JvmField val mode: Int = 420,
  @JvmField val symlinkTarget: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val kind: String = "file",
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val classPath: Boolean = true,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val normalizeTreeModes: Boolean = false,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val scope: String = PLUGIN_ASSET_SCOPE,
)

private object CompactAssetSerializer : KSerializer<PluginPackingAsset> {
  override val descriptor: SerialDescriptor = SerialDescriptor("org.jetbrains.intellij.build.devDist.CompactPluginPackingAsset", CompactAsset.serializer().descriptor)

  override fun serialize(encoder: Encoder, value: PluginPackingAsset) {
    val module = moduleOf(value.recipe)
    val compact = if (module != null && value == moduleJarAsset(module)) {
      CompactAsset(module = module)
    }
    else {
      CompactAsset(
        destination = value.destination,
        inputs = value.inputs.takeUnless { it == value.recipe?.sources?.map(JarSourceRecipe::input) },
        recipe = value.recipe,
        mode = value.mode,
        symlinkTarget = value.symlinkTarget,
        kind = value.kind,
        classPath = value.classPath,
        normalizeTreeModes = value.normalizeTreeModes,
        scope = value.scope,
      )
    }
    encoder.encodeSerializableValue(CompactAsset.serializer(), compact)
  }

  override fun deserialize(decoder: Decoder): PluginPackingAsset {
    val compact = decoder.decodeSerializableValue(CompactAsset.serializer())
    val module = compact.module
    if (module != null) {
      val plain = CompactAsset()
      require(
        compact.destination == null && compact.inputs == null && compact.recipe == null && compact.symlinkTarget == null &&
        compact.mode == plain.mode && compact.kind == plain.kind && compact.classPath == plain.classPath &&
        compact.normalizeTreeModes == plain.normalizeTreeModes && compact.scope == plain.scope
      ) { "A module jar asset states only its module: $module" }
      return moduleJarAsset(module)
    }
    val destination = requireNotNull(compact.destination) { "A projection asset requires a destination or a module" }
    val inputs = compact.inputs
                 ?: requireNotNull(compact.recipe) { "A projection asset without inputs requires a recipe: $destination" }.sources.map(JarSourceRecipe::input)
    return PluginPackingAsset(
      destination = destination,
      inputs = inputs,
      recipe = compact.recipe,
      mode = compact.mode,
      symlinkTarget = compact.symlinkTarget,
      kind = compact.kind,
      classPath = compact.classPath,
      normalizeTreeModes = compact.normalizeTreeModes,
      scope = compact.scope,
    )
  }
}

/** The codec of `PluginPackingProjection.assets`. */
@ApiStatus.Internal
object CompactPluginPackingAssetsSerializer : KSerializer<List<PluginPackingAsset>> by ListSerializer(CompactAssetSerializer)
