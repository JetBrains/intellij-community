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

/**
 * Represents <a href="http://semver.org">Semantic Version</a>.
 */
@Serializable(with = PluginVersionSerializer::class)
data class PluginVersion(
  val major: Int,
  val minor: Int,
  val patch: Int,
  val preRelease: String?,
) : Comparable<PluginVersion> {

  companion object {
    fun fromString(s: String): PluginVersion {
      val major = s.substringBefore('.').toIntOrNull()
      return when {
        major == null || major < 0 -> throw IllegalArgumentException("Cannot parse `$s` as PluginVersion, must start by a strictly positive integer")
        major <= 1 -> fromSemverString(s)
        else -> fromIntelliJUnifiedVersioningString(s)
      }
    }

    /**
     * Legacy build numbering of FL was using SemVer
     */
    private fun fromSemverString(s: String): PluginVersion {
      val majorEndIdx = s.indexOf('.')
      if (majorEndIdx >= 0) {
        val minorEndIdx = s.indexOf('.', majorEndIdx + 1)
        if (minorEndIdx >= 0) {
          val preReleaseIdx = s.indexOf('-', minorEndIdx + 1)
          val patchEndIdx = if (preReleaseIdx >= 0) preReleaseIdx else s.length
          val major = s.substring(0, majorEndIdx).toIntOrNull()
          val minor = s.substring(majorEndIdx + 1, minorEndIdx).toIntOrNull()
          val patch = s.substring(minorEndIdx + 1, patchEndIdx).toIntOrNull()
          val preRelease = if (preReleaseIdx >= 0) s.substring(preReleaseIdx + 1) else null
          if (major != null && minor != null && patch != null) {
            return PluginVersion(major = major, minor = minor, patch = patch, preRelease = preRelease)
          }
        }
      }
      throw IllegalArgumentException("Cannot parse `$s` as PluginVersion.")
    }

    /**
     * Build numbering of AIR and next products of the Fleet platform, using https://youtrack.jetbrains.com/articles/IJPL-A-109
     */
    private fun fromIntelliJUnifiedVersioningString(s: String): PluginVersion {
      val intComponents = s.split(".", limit = 3).map {
        it.toIntOrSnapshotOrNull() ?: throw IllegalArgumentException("Cannot parse `$s` as PluginVersion, all components must be integers")
      }
      return when (intComponents.size) {
        3 -> {
          val (major, minor, patch) = intComponents
          when (major) {
            UnifiedVersionComponent.Snapshot -> throw IllegalArgumentException("Cannot parse `$s` as PluginVersion, major cannot be '$SNAPSHOT'")
            is UnifiedVersionComponent.IntComponent -> when (minor) {
              UnifiedVersionComponent.Snapshot -> throw IllegalArgumentException("Cannot parse `$s` as PluginVersion, minor cannot be '$SNAPSHOT' if there is a patch number")
              is UnifiedVersionComponent.IntComponent -> when (patch) {
                is UnifiedVersionComponent.IntComponent -> PluginVersion(major = major.value,
                                                                         minor = minor.value,
                                                                         patch = patch.value,
                                                                         preRelease = null)
                UnifiedVersionComponent.Snapshot -> PluginVersion(major = major.value,
                                                                  minor = minor.value,
                                                                  patch = 0,
                                                                  preRelease = SNAPSHOT) // hackily set to `0`, I don't see what else we could do here
              }
            }
          }
        }
        2 -> { // nightly build
          val (major, minor) = intComponents
          when (major) {
            UnifiedVersionComponent.Snapshot -> throw IllegalArgumentException("Cannot parse `$s` as PluginVersion, major cannot be '$SNAPSHOT'")
            is UnifiedVersionComponent.IntComponent -> when (minor) {
              is UnifiedVersionComponent.IntComponent -> PluginVersion(major = major.value,
                                                                       minor = minor.value,
                                                                       patch = 0,
                                                                       preRelease = null)  // hackily set to `0`, I don't see what else we could do here
              UnifiedVersionComponent.Snapshot -> PluginVersion(major = major.value,
                                                                minor = 0,
                                                                patch = 0,
                                                                preRelease = SNAPSHOT)  // hackily set to `0`, I don't see what else we could do here
            }
          }
        }
        else -> throw IllegalArgumentException("Cannot parse `$s` as PluginVersion, must be either XXX.YYY or XXX.YYY.ZZZ")
      }
    }

    private const val SNAPSHOT = "SNAPSHOT"

    sealed class UnifiedVersionComponent {
      data class IntComponent(val value: Int) : UnifiedVersionComponent()
      data object Snapshot : UnifiedVersionComponent()
    }

    fun String.toIntOrSnapshotOrNull(): UnifiedVersionComponent? = when (this) {
      SNAPSHOT -> UnifiedVersionComponent.Snapshot
      else -> toIntOrNull()?.let { UnifiedVersionComponent.IntComponent(it) }
    }
  }

  val presentableText: String get() = versionString

  /**
   * String representation of this [PluginVersion] for Marketplace compatibility range fields of the plugin descriptor's JSON
   */
  val marketplaceCompatibilityRangeVersionString: String
    get() = buildVersionString(withSemverPatchEvenWhenZero = true)

  val versionString: String
    get() = buildVersionString(withSemverPatchEvenWhenZero = false)

  /**
   * Build the [String] representation of that [PluginVersion] used in business logic.
   *
   * Handling of the case where patch=0 depends on [withSemverPatchEvenWhenZero] parameter.
   *
   * Note: patch=0 usually happens when the original plugin version was a Nightly version.
   * Indeed, Nightly versions do not contain a patch component.
   * For backward compatibility, in such a case, we had to set it to 0 until we change [PluginVersion] altogether not to represent a SemVer.
   *
   * @param withSemverPatchEvenWhenZero whether to include a SemVer compatible `.0` patch when patch=0
   */
  private fun buildVersionString(withSemverPatchEvenWhenZero: Boolean): String = buildString {
    append(major)
    append(".")
    append(minor)
    if (withSemverPatchEvenWhenZero || patch != 0) {
      append(".")
      append(patch)
    }
    if (preRelease != null) {
      append("-$preRelease")
    }
  }

  override fun compareTo(other: PluginVersion): Int {
    val result = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    return if (result != 0) {
      result
    }
    else {
      comparePreRelease(preRelease, other.preRelease)
    }
  }

  private fun comparePreRelease(pre1: String?, pre2: String?): Int {
    return when {
      pre1 == pre2 -> 0
      pre1 == null -> 1
      pre2 == null -> -1
      else -> {
        val iterator1 = pre1.splitToSequence('.').iterator()
        val iterator2 = pre2.splitToSequence('.').iterator()

        while (iterator1.hasNext() && iterator2.hasNext()) {
          val segment1 = iterator1.next()
          val intSegment1 = segment1.toIntOrNull()
          val segment2 = iterator2.next()
          val intSegment2 = segment2.toIntOrNull()
          val result = when {
            intSegment1 != null && intSegment2 != null -> {
              intSegment1.compareTo(intSegment2)
            }
            intSegment1 == null && intSegment2 == null -> {
              segment1.compareTo(segment2)
            }
            intSegment1 == null -> {
              // According to SemVer specification numeric segments has lower precedence
              // than non-numeric segments
              1
            }
            else -> -1
          }
          if (result != 0) {
            return result
          }
        }
        return if (iterator1.hasNext()) {
          1
        }
        else {
          -1
        }
      }
    }
  }

  // should be in sync with https://github.com/JetBrains/intellij-plugin-verifier/blob/6a04cd7c94eb806877e26a093378eaf2b85e0d73/intellij-plugin-structure/structure-fleet/src/main/kotlin/com/jetbrains/plugin/structure/fleet/FleetPluginDescriptor.kt#L146
  fun toLong(): Long {
    val components = buildList {
      if (major != 0) {
        add(major)
      }
      if (minor != 0) {
        add(minor)
      }
      if (patch != 0) {
        add(patch)
      }
      if (preRelease != null) {
        add(SNAPSHOT_VALUE)
      }
    }
    return CompatibilityUtils.versionAsLong(components.toIntArray())
  }
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
  @Serializable(with = PluginVersionForCompatibilityRangeSerializer::class)
  val from: PluginVersion,
  @Serializable(with = PluginVersionForCompatibilityRangeSerializer::class)
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
sealed interface ResourcesEntry {
  @Serializable
  data class Content(val name: String, val content: String) : ResourcesEntry
  @Serializable
  data class RelativePath(val path: String): ResourcesEntry
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

private const val SNAPSHOT_VALUE = Int.MAX_VALUE

private object CompatibilityUtils {
  private const val MAX_BUILD_VALUE = 100000
  private const val MAX_COMPONENT_VALUE = 10000
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
