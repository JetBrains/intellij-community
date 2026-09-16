@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.dev

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant

internal const val DEV_PLUGIN_RESOURCE_TIMESTAMP_SCHEMA = "original-resource-file-time-v1"

private const val MAX_TIMESTAMP_METADATA_BYTES = 8192
private val timestampInteger = Regex("0|-?[1-9][0-9]*")

/** Validates the binding without opening either input. The metadata must be a separate file artifact. */
internal fun validateDevPluginResourceTimestampBinding(source: DevPluginResourceSource, catalogue: PreparationCatalogue) {
  val metadata = source.timestampMetadata ?: return
  require(source.entries.singleOrNull()?.kind == "file") { "Only a file resource can bind timestamp metadata" }
  catalogue.requireReference(source.input)
  require(metadata.artifact != source.input.artifact) { "Timestamp metadata must be a distinct input" }
  require(catalogue.requireReference(metadata).kind == "file") { "Timestamp metadata must have the file kind" }
}

/**
 * Stages one stable POSIX source with its logical name and exact declared time.
 * Version 1 requires sourceArtifact, sourcePath, epochSeconds, and nanos, plus the integer version 1.
 * The source must retain its original time. A transport that replaces that time requires a separate consistency contract.
 * Attribute and content checks detect ordinary mutations. They do not provide an atomic snapshot against arbitrary races.
 */
@ApiStatus.Internal
fun <T> withDevPluginResourceTimestamp(
  source: DevPluginResourceSource,
  path: Path,
  metadataPath: Path,
  scratch: Path,
  action: (Path) -> T,
): T {
  require(source.timestampMetadata != null) { "A file archive requires timestamp metadata" }
  require(!Files.isSameFile(path, metadataPath)) { "Timestamp metadata aliases the source" }
  val sourceAttributes = timestampInputAttributes(path)
  val metadataAttributes = timestampInputAttributes(metadataPath)
  val metadataBytes = readTimestampMetadata(metadataPath)
  val time = parseResourceTimestamp(metadataBytes, source.input)
  require(sourceAttributes.get("lastModifiedTime") == time) { "Stale timestamp metadata for '${source.resourcePath}'" }
  require(timestampInputAttributes(metadataPath) == metadataAttributes) { "Timestamp metadata changed during preparation" }

  val name = source.resourcePath.substringAfterLast('/')
  validatePreparationPath(name)
  val staged = Files.createDirectory(scratch.resolve("source")).resolve(name)
  Files.copy(path, staged)
  val mode = source.entries.single().mode
  require((sourceAttributes.get("mode") as Int) and 4095 == mode) { "The source mode changed" }
  Files.setPosixFilePermissions(staged, PosixFilePermission.entries.filterIndexedTo(HashSet()) { index, _ ->
    mode and (1 shl (8 - index)) != 0
  })
  setExactDevPluginResourceTimestamp(staged, time)

  fun validateInputs() {
    require(timestampInputAttributes(path) == sourceAttributes) { "The resource source changed during preparation" }
    require(Files.mismatch(path, staged) == -1L) { "The resource content changed during preparation" }
    require(timestampInputAttributes(path) == sourceAttributes) { "The resource source changed during content validation" }
    require(timestampInputAttributes(metadataPath) == metadataAttributes &&
            readTimestampMetadata(metadataPath).contentEquals(metadataBytes) &&
            timestampInputAttributes(metadataPath) == metadataAttributes) { "Timestamp metadata changed during preparation" }
  }
  validateInputs()
  val result = action(staged)
  validateInputs()
  return result
}

@ApiStatus.Internal
fun setExactDevPluginResourceTimestamp(staged: Path, time: FileTime) {
  try {
    Files.setLastModifiedTime(staged, time)
  }
  catch (exception: IOException) {
    throw IllegalArgumentException("The staging filesystem cannot preserve the exact resource timestamp: $time", exception)
  }
  require(Files.getLastModifiedTime(staged, NOFOLLOW_LINKS) == time) {
    "The staging filesystem cannot preserve the exact resource timestamp: $time"
  }
}

private fun timestampInputAttributes(path: Path): Map<String, Any> {
  val attributes = Files.readAttributes(path, "unix:mode,size,lastModifiedTime,ctime,fileKey,isRegularFile", NOFOLLOW_LINKS)
  require(attributes.get("isRegularFile") == true && attributes.get("fileKey") != null) { "The timestamp contract requires a regular POSIX file: $path" }
  return attributes
}

private fun readTimestampMetadata(path: Path): ByteArray {
  val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(MAX_TIMESTAMP_METADATA_BYTES + 1) }
  require(bytes.size <= MAX_TIMESTAMP_METADATA_BYTES) { "Timestamp metadata exceeds $MAX_TIMESTAMP_METADATA_BYTES bytes" }
  return bytes
}

@ApiStatus.Internal
fun parseResourceTimestamp(bytes: ByteArray, source: DevPluginReference): FileTime {
  require(bytes.size <= MAX_TIMESTAMP_METADATA_BYTES) { "Timestamp metadata exceeds $MAX_TIMESTAMP_METADATA_BYTES bytes" }
  val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(bytes)).toString()
  validateTimestampJsonStrings(text)
  val fields = Json.decodeFromString(ResourceTimestampFields, text)
  fun integer(name: String): Long {
    val value = fields.getValue(name)
    require(!value.isString && timestampInteger.matches(value.content)) { "Timestamp '$name' must be a canonical integer" }
    return requireNotNull(value.content.toLongOrNull()) { "Timestamp '$name' is outside the integer range" }
  }
  fun string(name: String): String {
    val value = fields.getValue(name)
    require(value.isString) { "Timestamp '$name' must be a string" }
    return value.content
  }
  require(integer("version") == 1L) { "Unsupported resource timestamp version" }
  val identity = DevPluginReference(string("sourceArtifact"), string("sourcePath"))
  require(identity.artifact.isNotBlank() && identity.artifact.trim() == identity.artifact &&
          identity.artifact.none { it == '\u0000' || it == '\r' || it == '\n' }) { "Invalid timestamp source identity" }
  if (identity.path.isNotEmpty()) validatePreparationPath(identity.path)
  require(identity == source) { "Timestamp metadata does not match the symbolic source reference" }
  val seconds = integer("epochSeconds")
  val nanos = integer("nanos")
  require(seconds in Instant.MIN.epochSecond..Instant.MAX.epochSecond && nanos in 0..999_999_999) { "Resource timestamp is out of range" }
  return FileTime.from(Instant.ofEpochSecond(seconds, nanos))
}

private fun validateTimestampJsonStrings(text: String) {
  var inString = false
  var escaped = false
  for (character in text) {
    if (!inString) {
      inString = character == '"'
      continue
    }
    require(character >= ' ') { "Timestamp metadata contains a literal control character in a JSON string" }
    if (escaped) {
      escaped = false
    }
    else {
      when (character) {
        '\\' -> escaped = true
        '"' -> inString = false
      }
    }
  }
}

private object ResourceTimestampFields : DeserializationStrategy<Map<String, JsonPrimitive>> {
  private val names = listOf("version", "sourceArtifact", "sourcePath", "epochSeconds", "nanos")
  override val descriptor = buildClassSerialDescriptor("DevPluginResourceTimestamp") {
    for (name in names) element<JsonElement>(name)
  }

  override fun deserialize(decoder: Decoder): Map<String, JsonPrimitive> {
    val fields = LinkedHashMap<String, JsonPrimitive>()
    decoder.decodeStructure(descriptor) {
      while (true) {
        val index = decodeElementIndex(descriptor)
        if (index == CompositeDecoder.DECODE_DONE) break
        require(index in names.indices) { "Unknown timestamp metadata field" }
        val name = names[index]
        val value = decodeSerializableElement(descriptor, index, JsonElement.serializer())
        require(value is JsonPrimitive) { "Timestamp '$name' must be a scalar" }
        require(fields.putIfAbsent(name, value) == null) { "Duplicate timestamp field '$name'" }
      }
    }
    require(fields.keys == names.toSet()) { "Timestamp metadata requires every version 1 field" }
    return fields
  }
}
