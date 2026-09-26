package fleet.bundles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class PluginVendor(val isSafeToReport: Boolean) {
  Platform(true), JetBrains(true), ThirdParty(false)
}

@Serializable(with = PluginNameSerializer::class)
data class PluginName(val name: String)

/**
 * VersionRequirement is what can present in extension's dependencies
 */
@Serializable(with = VersionSerializer::class)
sealed class VersionRequirement {
  abstract val version: PluginVersion

  data class CompatibleWith(override val version: PluginVersion) : VersionRequirement()
  data class Above(override val version: PluginVersion) : VersionRequirement()
}

sealed class UnifiedVersionComponent {
  abstract val stringRepresentation: String

  data class Number(val value: Int) : UnifiedVersionComponent() {
    override val stringRepresentation: String = value.toString()
  }

  data object Snapshot : UnifiedVersionComponent() {
    override val stringRepresentation: String = SNAPSHOT

    /**
     * Integer value of a SNAPSHOT component, the numeric value depends on in which position is this SNAPSHOT component, that position in
     * the original version string must be provided by [componentIndex].
     */
    fun valueForIndex(componentIndex: Int): Int = when (componentIndex) {
      1 -> CompatibilityUtils.MAX_FLEET_BUILD_VALUE
      2 -> CompatibilityUtils.MAX_COMPONENT_VALUE - 1
      else -> throw IllegalArgumentException("Invalid component index for '$SNAPSHOT': $componentIndex")
    }
  }

  companion object {
    private const val SNAPSHOT = "SNAPSHOT"

    fun fromString(s: String?): UnifiedVersionComponent? = when (s) {
      null -> null
      SNAPSHOT -> Snapshot
      else -> when (val i = s.toIntOrNull()) {
        null -> throw IllegalArgumentException("Cannot parse `$s` as UnifiedVersionComponent, it must be an integer or 'SNAPSHOT'")
        else -> Number(i)
      }
    }
  }
}

@Serializable(with = PluginVersionSerializer::class)
data class PluginVersion(
  val component0: UnifiedVersionComponent.Number,
  val component1: UnifiedVersionComponent,
  val component2: UnifiedVersionComponent?,
) : Comparable<PluginVersion> {
  val isSnapshot: Boolean = component2 is UnifiedVersionComponent.Snapshot || component1 is UnifiedVersionComponent.Snapshot

  companion object {
    /**
     * Build numbering of AIR and next products of the Fleet platform, using https://youtrack.jetbrains.com/articles/IJPL-A-109
     */
    fun fromString(s: String): PluginVersion {
      val components = s.split(".")
      require(components.size == 2 || components.size == 3) {
        "Cannot parse `$s` as PluginVersion, must be either X.Y or X.Y.Z"
      }
      val component0 = UnifiedVersionComponent.fromString(components[0])
      require(component0 != null) {
        "Cannot parse `$s` as PluginVersion, component 1 must be an integer"
      }
      require(component0 is UnifiedVersionComponent.Number) {
        "Cannot parse `$s` as PluginVersion, component 1 must be an integer, it cannot be SNAPSHOT"
      }
      val component1 = UnifiedVersionComponent.fromString(components[1])
      require(component1 != null) {
        "Cannot parse `$s` as PluginVersion, component 2 must be an integer or 'SNAPSHOT'"
      }
      val component2 = UnifiedVersionComponent.fromString(components.getOrNull(2))
      require(component2 == null || component1 !is UnifiedVersionComponent.Snapshot) {
        "Cannot parse `$s` as PluginVersion, component 2 cannot be SNAPSHOT if a component 3 is specified"
      }
      require(component1 !is UnifiedVersionComponent.Number || component1.value <= CompatibilityUtils.MAX_FLEET_BUILD_VALUE) {
        "Cannot parse `$s` as PluginVersion, component 2 cannot be greater than ${CompatibilityUtils.MAX_FLEET_BUILD_VALUE}"
      }
      require(component2 == null || component2 !is UnifiedVersionComponent.Number || component2.value < CompatibilityUtils.MAX_COMPONENT_VALUE) {
        "Cannot parse `$s` as PluginVersion, component 3 cannot be greater than ${CompatibilityUtils.MAX_COMPONENT_VALUE}"
      }
      return PluginVersion(
        component0 = component0,
        component1 = component1,
        component2 = component2,
      )
    }

  }

  val presentableText: String get() = versionString

  /**
   * String representation of this [PluginVersion] for Marketplace compatibility range fields of the plugin descriptor's JSON
   *
   * @param lowerBound if true, the returned string will be a lower bound for the compatibility range, if false, it will be an upper bound
   */
  fun marketplaceCompatibilityRangeVersionString(lowerBound: Boolean): String = listOfNotNull(
    component0.value,
    when (component1) {
      is UnifiedVersionComponent.Number -> component1.value
      UnifiedVersionComponent.Snapshot -> when {
        lowerBound -> 0
        else -> UnifiedVersionComponent.Snapshot.valueForIndex(1)
      }
    },
    when (component2) {
      null -> when (component1) {
        is UnifiedVersionComponent.Number -> 0
        UnifiedVersionComponent.Snapshot -> when {
          lowerBound -> 0
          else -> UnifiedVersionComponent.Snapshot.valueForIndex(2)
        }
      }
      is UnifiedVersionComponent.Number -> component2.value
      UnifiedVersionComponent.Snapshot -> when {
        lowerBound -> 0
        else -> UnifiedVersionComponent.Snapshot.valueForIndex(2)
      }
    },
  ).joinToString(".")

  val versionString: String by lazy {
    listOfNotNull(component0, component1, component2).joinToString(".") { it.stringRepresentation }
  }

  override fun compareTo(other: PluginVersion): Int = toLong().compareTo(other.toLong())

  // should be in sync with https://github.com/JetBrains/intellij-plugin-verifier/blob/6a04cd7c94eb806877e26a093378eaf2b85e0d73/intellij-plugin-structure/structure-fleet/src/main/kotlin/com/jetbrains/plugin/structure/fleet/FleetPluginDescriptor.kt#L146
  private val longRepresentation: Long by lazy {
    val values = listOfNotNull(
      component0.value,
      when (component1) {
        is UnifiedVersionComponent.Number -> component1.value
        UnifiedVersionComponent.Snapshot -> UnifiedVersionComponent.Snapshot.valueForIndex(1)
      },
      when (component2) {
        null -> null
        is UnifiedVersionComponent.Number -> component2.value
        UnifiedVersionComponent.Snapshot -> UnifiedVersionComponent.Snapshot.valueForIndex(2)
      },
    )
    CompatibilityUtils.versionAsLong(values.toIntArray())
  }

  // should be in sync with https://github.com/JetBrains/intellij-plugin-verifier/blob/6a04cd7c94eb806877e26a093378eaf2b85e0d73/intellij-plugin-structure/structure-fleet/src/main/kotlin/com/jetbrains/plugin/structure/fleet/FleetPluginDescriptor.kt#L146
  fun toLong(): Long = longRepresentation
}

/**
 * Represents a [PluginDescriptor]'s value
 */
@Serializable(with = PluginDescriptorSerializer::class)
data class PluginDescriptor(
  val formatVersion: Int = 0,
  val name: PluginName,
  val version: PluginVersion,
  val deps: Map<PluginName, VersionRequirement> = emptyMap(),
  val compatibleShipVersionRange: ShipVersionRange? = null,
  val signature: PluginSignature? = null,
  val meta: Map<String, String> = emptyMap(),
) {
  override fun toString(): String = prettyJson.encodeToString(serializer(), this)
}

private val prettyJson = Json {
  prettyPrint = true
}

@OptIn(ExperimentalSerializationApi::class)
private val defaultJson = Json { // we cannot depend on `fleet.util.serialization.DefaultJson` from `fleet.bundles`
  ignoreUnknownKeys = true
  encodeDefaults = true
  explicitNulls = false
}

val PluginDescriptor.partsCoordinates: Coordinates? get() = metaAsCoordinates(KnownMeta.PartsCoordinates)
val PluginDescriptor.defaultIcon: Coordinates? get() = metaAsCoordinates(KnownMeta.DefaultIconCoordinates)
val PluginDescriptor.darkIcon: Coordinates? get() = metaAsCoordinates(KnownMeta.DarkIconCoordinates)

fun PluginDescriptor.metaAsCoordinates(metaKey: String): Coordinates? = meta[metaKey]?.let { serializedCoordinates ->
  defaultJson.decodeFromString(Coordinates.serializer(), serializedCoordinates)
}

private const val JETBRAINS_VENDOR = "JetBrains"

fun PluginDescriptor.getVendorType(): PluginVendor {
  val vendorId = this.meta[KnownMeta.VendorId]
  return when (vendorId) {
    null -> PluginVendor.Platform
    JETBRAINS_VENDOR -> PluginVendor.JetBrains
    else -> {
      PluginVendor.ThirdParty
    }
  }
}

@Serializable(with = PluginSignatureSerializer::class)
data class PluginSignature(val bytes: ByteArray) {
  override fun equals(other: Any?): Boolean =
    other is PluginSignature && other.bytes.contentEquals(bytes)

  override fun hashCode(): Int =
    bytes.contentHashCode()

  override fun toString(): String =
    "PluginSignature(size=${bytes.size}, hash=${hashCode().toString(16)}"
}

@Serializable
data class ShipVersionRange(
  @Serializable(with = PluginVersionForFromCompatibilityRangeSerializer::class)
  val from: PluginVersion,
  @Serializable(with = PluginVersionForToCompatibilityRangeSerializer::class)
  val to: PluginVersion,
)

@Serializable(with = LayerSelectorSerializer::class)
data class LayerSelector(val selector: String)

@Serializable
data class ModuleCoordinates(
  val coordinates: Coordinates,
  val serializedModuleDescriptor: String?,
)

@Serializable(with = PluginLayerSerializer::class)
data class PluginLayer(
  val modulePath: Set<ModuleCoordinates>,
  val modules: Set<String>,
  val resources: Set<Coordinates>,
)

@Serializable(with = PluginPartsSerializer::class)
data class PluginParts(val layers: Map<LayerSelector, PluginLayer>)

@Serializable
sealed interface ResourcesBundle {
  @Serializable
  data class Tar(val path: String) : ResourcesBundle

  @Serializable
  data class Plain(val map: Map<String, ResourcesEntry>) : ResourcesBundle
}

@Serializable
sealed interface ResourcesEntry {
  @Serializable
  data class Content(val content: String) : ResourcesEntry

  @Serializable
  data class RelativePath(val path: String) : ResourcesEntry
}

@Serializable
sealed interface Coordinates {
  val meta: Map<String, String>

  // to reference e.g. a plugin file in marketplace (which might also be in code-cache already)
  @Serializable
  @SerialName("Remote")
  data class Remote(val url: String, val hash: String, override val meta: Map<String, String> = emptyMap()) : Coordinates {
    companion object {
      const val HASH_ALGORITHM: String = "SHA3-256"
    }
  }

  // to reference a folder with classes, should be used when running from sources only
  @Serializable
  @SerialName("Local")
  data class Local(val path: String, override val meta: Map<String, String> = emptyMap()) : Coordinates
}

class KnownCoordinatesMeta {
  companion object {
    const val Platforms: String = "platforms"
  }
}

@Serializable(with = PluginSetSerializer::class)
data class PluginSet(
  val shipVersions: Set<String>,
  val plugins: Set<PluginDescriptor>,
)

private object CompatibilityUtils {
  @Deprecated(message = "Must be replaced by `MAX_BUILD_VALUE` once Marketplace stops validating on that minor number")
  const val MAX_FLEET_BUILD_VALUE = 8191
  const val MAX_BUILD_VALUE = 100000
  const val MAX_COMPONENT_VALUE = 10000
  const val SNAPSHOT_VALUE = Int.MAX_VALUE
  private val NUMBERS_OF_NINES by lazy { initNumberOfNines() }

  private fun initNumberOfNines(): IntArray {
    val numbersOfNines = ArrayList<Int>()
    var i = 99999
    val maxIntDiv10 = Int.MAX_VALUE / 10
    while (i < maxIntDiv10) {
      i = i * 10 + 9
      numbersOfNines.add(i)
    }

    return numbersOfNines.toIntArray()
  }

  fun versionAsLong(components: IntArray): Long {
    val baselineVersion = components.getOrElse(0) { 0 }
    val build = components.getOrElse(1) { 0 }
    var longVersion = branchBuildAsLong(baselineVersion, build)

    if (components.size >= 3) {
      val component = components[2]
      longVersion += if (component == Int.MAX_VALUE) MAX_COMPONENT_VALUE - 1 else component
    }

    return longVersion
  }

  private fun isNumberOfNines(p: Int) = NUMBERS_OF_NINES.any { it == p }

  private fun branchBuildAsLong(branch: Int, build: Int): Long {
    val result = if (build == Int.MAX_VALUE || isNumberOfNines(build)) {
      MAX_BUILD_VALUE - 1
    }
    else {
      build
    }

    return branch.toLong() * MAX_COMPONENT_VALUE * MAX_BUILD_VALUE + result.toLong() * MAX_COMPONENT_VALUE
  }
}
