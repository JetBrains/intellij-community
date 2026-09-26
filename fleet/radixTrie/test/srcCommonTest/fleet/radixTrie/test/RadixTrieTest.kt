package fleet.radixTrie.test

import fleet.radixTrie.RadixTrie
import fleet.radixTrie.RadixTrieReduceDecision
import fleet.radixTrie.get
import fleet.radixTrie.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class RadixTrieTest {
  @Test
  fun radix() {
    val e = Any()
    var t = RadixTrie.empty<String>()
    var v: String?
    repeat(1000000) { i ->
      v = "hey $i"
      t = t.update(e, i) { v }
    }
    repeat(1000000) { i ->
      assertEquals("hey $i", t[i])
    }
    val kvs = HashMap<Int, Any?>()
    t.reduce { k, value ->
      kvs[k] = value
      RadixTrieReduceDecision.Continue
    }
    repeat(1000000) { i ->
      assertEquals("hey $i", kvs[i])
    }
    repeat(1000000) { i ->
      t = t.update(e, i) { null }
    }
    repeat(1000000) { i ->
      assertNull(t[i])
    }
    t.reduce { k, _ ->
      fail("should be empty: $k")
    }
  }
}
