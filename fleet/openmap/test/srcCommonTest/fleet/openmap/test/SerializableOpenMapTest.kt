package fleet.openmap.test

import fleet.openmap.OpenMapView
import fleet.openmap.SerializableKey
import fleet.openmap.SerializableOpenMap
import fleet.openmap.SerializableOpenMapSerializer
import fleet.openmap.assoc
import fleet.openmap.dissoc
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

interface DurableOpenMapTestDomain
typealias DMap = SerializableOpenMap<DurableOpenMapTestDomain>
val testKeyInt = SerializableKey<Int, DurableOpenMapTestDomain>("int", Int.serializer())
val testKeyString = SerializableKey<String, DurableOpenMapTestDomain>("string", String.serializer())

class SerializableOpenMapTest {

  @Test
  fun `test we could put two values, serialize and deserialize`() {
    val dmap = DMap().assoc(testKeyInt, 42).assoc(testKeyString, "hello")
    val json = Json.encodeToString(SerializableOpenMapSerializer(), dmap)
    val deser = Json.decodeFromString(SerializableOpenMapSerializer(), json) as DMap
    assertEquals(42, deser[testKeyInt])
    assertEquals("hello", deser[testKeyString])
  }

  @Test
  fun  `preserve format of serializable open map`() {
    val text = """{"int":42,"string":"hello"}"""
    val deser = Json.decodeFromString(SerializableOpenMapSerializer(), text) as DMap
    assertEquals(42, deser[testKeyInt])
    assertEquals("hello", deser[testKeyString])
  }

  @Test
  fun `test we could manipulate view`() {
    var dmap: OpenMapView<DurableOpenMapTestDomain> = DMap().assoc(testKeyInt, 42).assoc(testKeyString, "hello")
    dmap = dmap.dissoc(testKeyInt)
    assertNull(dmap[testKeyInt])
    dmap = dmap.assoc(testKeyInt, 12)
    assertEquals(12, dmap[testKeyInt])
    dmap = dmap.dissoc(testKeyInt)
    assertNull(dmap[testKeyInt])
    assertEquals("hello", dmap[testKeyString])
  }
}