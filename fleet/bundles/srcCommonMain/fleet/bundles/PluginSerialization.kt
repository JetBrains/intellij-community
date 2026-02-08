package fleet.bundles

import fleet.bifurcan.toSortedMap
import fleet.bifurcan.toSortedSet
import fleet.util.Base64WithOptionalPadding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.io.encoding.ExperimentalEncodingApi

internal class VersionSerializer : StringSerializer<VersionRequirement>(
  toString = {
    when (it) {
      is VersionRequirement.CompatibleWith -> it.version.versionString
      is VersionRequirement.Above -> it.version.versionString + "+"
    }
  },
  fromString = {
    when {
      it.endsWith("+") -> VersionRequirement.Above(PluginVersion.fromString(it.dropLast(1)))
      else -> VersionRequirement.CompatibleWith(PluginVersion.fromString(it))
    }
  }
)

internal class PluginNameSerializer : StringSerializer<PluginName>(PluginName::name, ::PluginName)

internal class PluginVersionSerializer : StringSerializer<PluginVersion>({ it.versionString }, { PluginVersion.fromString(it) })

/**
 * Serialize a [PluginVersion] using the string representation compatible with Marketplace compatibility range fields of the plugin descriptor's JSON
 */
// TODO: remove this and its usages once Marketplace supports Nightly two digit versions in Fleet's compatibility ranges as well
internal class PluginVersionForCompatibilityRangeSerializer : StringSerializer<PluginVersion>({ it.marketplaceCompatibilityRangeVersionString }, { PluginVersion.fromString(it) })

internal class LayerSelectorSerializer : StringSerializer<LayerSelector>(LayerSelector::selector, ::LayerSelector)

internal object PluginLayerSerializer : DataSerializer<PluginLayer, PluginLayerSurrogate>(
  serializer = PluginLayerSurrogate.serializer(),
  toData = PluginLayerSurrogate::fromPluginLayer,
  fromData = PluginLayerSurrogate::toPluginLayer,
)

/**
 * Surrogate of [PluginLayer] ensuring sorted [Set] at construction to allow stability at serialization
 */
@Serializable
@SerialName("PluginLayer")
data class PluginLayerSurrogate(
  private val modulePath: Set<ModuleCoordinates>,
  private val modules: Set<String>,
  private val resources: Set<Coordinates> = emptySet(),
) {
  fun toPluginLayer(): PluginLayer = PluginLayer(
    modulePath = modulePath,
    modules = modules,
    resources = resources,
  )

  companion object {
    fun fromPluginLayer(pl: PluginLayer): PluginLayerSurrogate = PluginLayerSurrogate(
      modulePath = pl.modulePath.toSortedSet(compareBy(coordinatesComparator) { it.coordinates }),
      modules = pl.modules.toSortedSet(),
      resources = pl.resources.toSortedSet(coordinatesComparator)
    )
  }
}

private val coordinatesComparator = compareBy<Coordinates> {
  when (it) {
    is Coordinates.Local -> it.path
    is Coordinates.Remote -> it.url
  }
}

internal object PluginPartsSerializer : DataSerializer<PluginParts, PluginPartsSurrogate>(
  serializer = PluginPartsSurrogate.serializer(),
  toData = PluginPartsSurrogate::fromPluginParts,
  fromData = PluginPartsSurrogate::toPluginParts,
)

/**
 * Surrogate of [PluginParts] ensuring sorted [Map] at construction to allow stability at serialization
 */
@Serializable
@SerialName("PluginDescriptor")
internal data class PluginPartsSurrogate(
  val layers: Map<LayerSelector, PluginLayer>,
) {
  fun toPluginParts(): PluginParts = PluginParts(
    layers = layers
  )

  companion object {
    fun fromPluginParts(pp: PluginParts): PluginPartsSurrogate = PluginPartsSurrogate(
      layers = pp.layers.toSortedMap(compareBy { selector -> selector.selector })
    )
  }
}

internal object PluginDescriptorSerializer : DataSerializer<PluginDescriptor, PluginDescriptorSurrogate>(
  serializer = PluginDescriptorSurrogate.serializer(),
  toData = PluginDescriptorSurrogate::fromPluginDescriptor,
  fromData = PluginDescriptorSurrogate::toPluginDescriptor,
)

/**
 * Surrogate of [PluginDescriptor] ensuring sorted [Map] and [Set] at construction to allow stability at serialization
 */
@Serializable
@SerialName("PluginDescriptor")
internal data class PluginDescriptorSurrogate(
  private val formatVersion: Int = 0,
  @SerialName("id")
  private val name: PluginName,
  private val version: PluginVersion,
  @SerialName("dependencies")
  private val deps: Map<PluginName, VersionRequirement> = emptyMap(),
  private val compatibleShipVersionRange: ShipVersionRange? = null,
  private val signature: PluginSignature? = null,
  private val meta: Map<String, String> = emptyMap(),
) {
  fun toPluginDescriptor(): PluginDescriptor = PluginDescriptor(
    formatVersion = formatVersion,
    name = name,
    version = version,
    deps = deps,
    compatibleShipVersionRange = compatibleShipVersionRange,
    signature = signature,
    meta = meta,
  )

  companion object {
    fun fromPluginDescriptor(pd: PluginDescriptor): PluginDescriptorSurrogate = PluginDescriptorSurrogate(
      formatVersion = pd.formatVersion,
      name = pd.name,
      version = pd.version,
      deps = pd.deps.toSortedMap(compareBy { pluginName -> pluginName.name }),
      compatibleShipVersionRange = pd.compatibleShipVersionRange,
      signature = pd.signature,
      meta = pd.meta.toSortedMap(),
    )
  }
}

internal object PluginSetSerializer : DataSerializer<PluginSet, PluginSetSurrogate>(
  serializer = PluginSetSurrogate.serializer(),
  toData = PluginSetSurrogate::fromPluginSet,
  fromData = PluginSetSurrogate::toPluginSet,
)

/**
 * Suroggate of [PluginSet] ensuring sorted [Set] at construction to allow stability at serialization
 */
@Serializable
@SerialName("PluginSet")
internal class PluginSetSurrogate(
  private val shipVersions: Set<String>,
  private val plugins: Set<PluginDescriptor>,
) {
  fun toPluginSet(): PluginSet = PluginSet(
    shipVersions = shipVersions,
    plugins = plugins,
  )

  companion object {
    fun fromPluginSet(ps: PluginSet): PluginSetSurrogate = PluginSetSurrogate(
      shipVersions = ps.shipVersions.toSortedSet(),
      plugins = ps.plugins.toSortedSet(compareBy(PluginDescriptor::encodeToString))
    )
  }
}

@OptIn(ExperimentalEncodingApi::class)
internal object PluginSignatureSerializer : DataSerializer<PluginSignature, String>(
  serializer = String.serializer(),
  toData = { value -> Base64WithOptionalPadding.encode(value.bytes) },
  fromData = { data -> PluginSignature(Base64WithOptionalPadding.decode(data)) }
)

open class StringSerializer<T>(
  toString: (T) -> String,
  fromString: (String) -> T,
) : DataSerializer<T, String>(String.serializer(), toString, fromString)

open class DataSerializer<T, D>(
  val serializer: KSerializer<D>,
  val toData: (T) -> D,
  val fromData: (D) -> T,
) : KSerializer<T> {

  override val descriptor: SerialDescriptor
    get() = serializer.descriptor

  override fun deserialize(decoder: Decoder): T {
    return fromData(serializer.deserialize(decoder))
  }

  override fun serialize(encoder: Encoder, value: T) {
    serializer.serialize(encoder, toData(value))
  }
}

class KnownMeta {
  companion object {
    const val ReadableName: String = "readableName"
    const val Description: String = "description"
    const val DocumentationUrl: String = "documentation-url"

    /**
     * Equivalent to vendor `publicName` in Marketplace
     */
    const val VendorPublicName: String = "vendor"

    /**
     * Equivalent to vendor `name` in Marketplace
     */
    const val VendorId: String = "vendorName"
    const val Visible: String = "visible"

    const val FrontendOnly: String = "frontend-only"

    const val PartsCoordinates: String = "partsCoordinates"

    const val DefaultIconCoordinates: String = "defaultIconCoordinates"
    const val DarkIconCoordinates: String = "darkIconCoordinates"

    /**
     * A set of Fleet product codes with which this plugin is compatible, represented as a comma-separated string
     *
     * Used by Marketplace as a discriminant for version parsing.
     * Format between FL and other Fleet products is no the same, FL uses SemVer, other Fleet products uses IntelliJ versioning.
     */
    const val SupportedProducts: String = "supportedProducts"
  }
}

internal class JsonFormat {
  companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

fun PluginDescriptor.Companion.decodeFromString(s: String): PluginDescriptor = JsonFormat.json.decodeFromString(serializer(), s)

fun PluginDescriptor.encodeToString(): String = JsonFormat.json.encodeToString(PluginDescriptor.serializer(), this)
fun PluginDescriptor.encodeToSignableString(): String = copy(signature = null, compatibleShipVersionRange = null).encodeToString()
