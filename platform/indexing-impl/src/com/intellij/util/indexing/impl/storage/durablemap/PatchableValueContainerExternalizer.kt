// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap

import com.intellij.platform.util.io.storages.DataExternalizerEx
import com.intellij.platform.util.io.storages.DataExternalizerEx.KnownSizeRecordWriter
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap.PatchableValueExternalizer
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer
import com.intellij.util.indexing.impl.UpdatableValueContainer
import com.intellij.util.indexing.impl.ValueContainerExternalizer
import com.intellij.util.indexing.impl.ValueContainerInputRemapping.IDENTITY
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.UnsyncByteArrayOutputStream
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Uses the container format without loading the snapshot during a patch write.
 * A replacement must invalidate the input ID before adding its new value.
 */
@ApiStatus.Internal
class PatchableValueContainerExternalizer<Value>(
  private val valueExternalizer: DataExternalizer<Value>
) : PatchableValueExternalizer<UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>> {

  private val valueExternalizerEx = DataExternalizerEx.adapt(
    ValueContainerExternalizer(valueExternalizer, /*inputRemapping: */IDENTITY)
  )

  @Throws(IOException::class)
  override fun read(input: ByteBuffer): UpdatableValueContainer<Value> = valueExternalizerEx.read(input)

  @Throws(IOException::class)
  override fun writerFor(value: UpdatableValueContainer<Value>): KnownSizeRecordWriter = valueExternalizerEx.writerFor(value)

  @Throws(IOException::class)
  override fun writerForPatch(patch: ChangeTrackingValueContainer<Value>): KnownSizeRecordWriter {
    val stream = UnsyncByteArrayOutputStream()
    DataOutputStream(stream).use { patch.saveDiffTo(it, valueExternalizer) }
    return DataExternalizerEx.fromBytes(stream.toByteArraySequence())
  }
}
