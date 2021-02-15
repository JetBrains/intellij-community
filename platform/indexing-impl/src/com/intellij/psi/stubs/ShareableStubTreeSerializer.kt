// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import org.jetbrains.annotations.NotNull
import java.io.*

class ShareableStubTreeSerializer : StubTreeSerializer {
  private val serializationManager = SerializationManagerEx.getInstanceEx() as SerializationManagerImpl
  private val serializer = object : StubTreeSerializerBase<FileLocalStringEnumerator>() {
    override fun readSerializationState(stream: StubInputStream): FileLocalStringEnumerator {
      val enumerator = FileLocalStringEnumerator(false)
      enumerator.read(stream) { it }
      return enumerator
    }

    override fun createSerializationState(): FileLocalStringEnumerator =
      FileLocalStringEnumerator(true)

    override fun saveSerializationState(state: FileLocalStringEnumerator, stream: DataOutputStream) =
      state.write(stream)

    override fun writeSerializerId(serializer: @NotNull ObjectStubSerializer<Stub, Stub>,
                                   state: @NotNull FileLocalStringEnumerator): Int =
      state.enumerate(serializationManager.getSerializerName(serializer)!!)

    override fun getClassByIdLocal(localId: Int, parentStub: Stub?, state: FileLocalStringEnumerator): ObjectStubSerializer<*, Stub> {
      val serializerName = state.valueOf(localId)
                           ?: throw SerializerNotFoundException("Can't find serializer for local id $localId")
      @Suppress("UNCHECKED_CAST")
      return serializationManager.getSerializer(serializerName) as ObjectStubSerializer<*, Stub>
    }
  }

  override fun serialize(rootStub: Stub, stream: OutputStream) {
    serializationManager.initSerializers()
    serializer.serialize(rootStub, stream)
  }

  @Throws(SerializerNotFoundException::class)
  override fun deserialize(stream: @NotNull InputStream): @NotNull Stub {
    serializationManager.initSerializers()
    return serializer.deserialize(stream)
  }

}