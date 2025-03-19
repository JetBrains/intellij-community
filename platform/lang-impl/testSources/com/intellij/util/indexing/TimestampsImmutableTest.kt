// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.io.DataInputOutputUtil
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

@RunWith(JUnit4::class)
class TimestampsImmutableTest {
  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @Test
  fun testSerializationDeserialization() {
    val idx1 = ID.create<Any, Any>("testSerializationDeserialization.1")
    val idx2 = ID.create<Any, Any>("testSerializationDeserialization.2")
    val idx3 = ID.create<Any, Any>("testSerializationDeserialization.3")
    val idx4 = ID.create<Any, Any>("testSerializationDeserialization.4")
    val idx5 = ID.create<Any, Any>("testSerializationDeserialization.5")
    val timeBase = DataInputOutputUtil.timeBase + ID.MAX_NUMBER_OF_INDICES

    val original = Timestamps()
    original.set(idx1, timeBase + 1)
    original.set(idx2, timeBase + 2)
    original.set(idx3, IndexingStamp.HAS_NO_INDEXED_DATA_STAMP)
    original.set(idx4, IndexingStamp.INDEX_DATA_OUTDATED_STAMP)
    original.set(idx5, timeBase + 3)
    original.set(idx5, IndexingStamp.INDEX_DATA_OUTDATED_STAMP) // This is not a mistake. We overwrite previous value

    assertEquals(IndexingStamp.INDEX_DATA_OUTDATED_STAMP, original.get(idx5))

    val immutable = original.toImmutable()

    val out = ByteArrayOutputStream()
    DataOutputStream(out).use { immutable.writeToStream(it) }

    val deserialized = TimestampsImmutable.readTimestamps(ByteBuffer.wrap(out.toByteArray()))

    assertEquals(immutable, deserialized)
    assertEquals(immutable.hashCode(), deserialized.hashCode())
  }

  @Test
  fun testSerializationDeserializationEmpty() {

    val immutable = Timestamps().toImmutable()

    val out = ByteArrayOutputStream()
    DataOutputStream(out).use { immutable.writeToStream(it) }

    val deserialized = TimestampsImmutable.readTimestamps(ByteBuffer.wrap(out.toByteArray()))

    assertEquals(immutable, deserialized)
    assertEquals(immutable.hashCode(), deserialized.hashCode())
  }
}