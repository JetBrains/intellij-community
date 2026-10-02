package com.jetbrains.lsp.test

import com.jetbrains.lsp.protocol.DefinitionRequestType
import com.jetbrains.lsp.protocol.DocumentUri
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.Location
import com.jetbrains.lsp.protocol.LocationLink
import com.jetbrains.lsp.protocol.LocationOrLink
import com.jetbrains.lsp.protocol.Position
import com.jetbrains.lsp.protocol.Range
import com.jetbrains.lsp.protocol.URI
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The `textDocument/definition` result decodes [Location]s and [LocationLink]s, told apart by the `targetUri` member. */
class LocationOrLinkSerializerTest {

  private val r = """{"start":{"line":1,"character":2},"end":{"line":3,"character":4}}"""
  private val o = """{"start":{"line":0,"character":5},"end":{"line":0,"character":9}}"""
  private val range = Range(Position(1, 2), Position(3, 4))
  private val origin = Range(Position(0, 5), Position(0, 9))
  private val uri = DocumentUri(URI("file:///a.kt"))

  private val compact = Json(LSP.json) { prettyPrint = false }

  private val location = Location(uri, range)
  private val link = LocationLink(originSelectionRange = origin, targetUri = uri, targetRange = range, targetSelectionRange = range)

  @Test
  fun `locations round-trip`() {
    assertRoundTrip(listOf(location, Location(uri, origin)))
  }

  @Test
  fun `links round-trip, also with no origin`() {
    assertRoundTrip(listOf(link, link.copy(originSelectionRange = null)))
  }

  @Test
  fun `empty result round-trips`() {
    assertRoundTrip(emptyList())
  }

  @Test
  fun `targetUri picks the link in any member order`() {
    val payload = """[{"targetRange":$r,"targetSelectionRange":$r,"originSelectionRange":$o,"targetUri":"file:///a.kt"}]"""
    assertEquals(listOf<LocationOrLink>(link), decodeAll(payload))
  }

  @Test
  fun `object without targetUri is a location`() {
    assertEquals(listOf<LocationOrLink>(location), decodeAll("""[{"range":$r,"uri":"file:///a.kt","unknown":{"targetUri":1}}]"""))
  }

  @Test
  fun `link encodes the link members only`() {
    val json = compact.encodeToString(DefinitionRequestType.resultSerializer, listOf(link))
    assertEquals("""[{"originSelectionRange":$o,"targetUri":"file:///a.kt","targetRange":$r,"targetSelectionRange":$r}]""", json)
  }

  @Test
  fun `location encodes like the plain location serializer`() {
    val json = compact.encodeToString(DefinitionRequestType.resultSerializer, listOf(location))
    assertEquals("[${compact.encodeToString(Location.serializer(), location)}]", json)
  }

  @Test
  fun `wrong shapes fail`() {
    for (payload in listOf("[1]", "[\"s\"]", "[[]]", "[{}]", """[{"targetUri":"file:///a.kt"}]""")) {
      assertFailsWith<SerializationException>(payload) { LSP.json.decodeFromString(DefinitionRequestType.resultSerializer, payload) }
    }
  }

  @Test
  fun `each kind is picked`() {
    assertIs<Location>(decodeAll("[${LSP.json.encodeToString(Location.serializer(), location)}]").single())
    assertIs<LocationLink>(decodeAll("[${LSP.json.encodeToString(LocationLink.serializer(), link)}]").single())
  }

  /** Decodes [payload] with `decodeFromString`, and `decodeFromJsonElement`; both must agree. */
  private fun decodeAll(payload: String): List<LocationOrLink> {
    val serializer = DefinitionRequestType.resultSerializer
    val string = LSP.json.decodeFromString(serializer, payload)
    assertEquals(string, LSP.json.decodeFromJsonElement(serializer, LSP.json.parseToJsonElement(payload)), "tree: $payload")
    return string
  }

  private fun assertRoundTrip(value: List<LocationOrLink>) {
    val json = LSP.json.encodeToString(DefinitionRequestType.resultSerializer, value)
    assertEquals(value, decodeAll(json), json)
  }
}
