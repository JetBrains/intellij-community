// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

final class StubSerializationHelper extends StubTreeSerializerBase<IntEnumerator> {
  @NotNull
  private final StubSerializerEnumerator myEnumerator;

  StubSerializationHelper(@NotNull StubSerializerEnumerator enumerator) {
    myEnumerator = enumerator;
  }

  @NotNull
  @Override
  protected IntEnumerator readSerializationState(@NotNull StubInputStream stream) throws IOException {
    return IntEnumerator.read(stream);
  }

  @NotNull
  @Override
  protected IntEnumerator createSerializationState() {
    return new IntEnumerator();
  }

  @Override
  protected void saveSerializationState(@NotNull IntEnumerator enumerator, @NotNull DataOutputStream stream) throws IOException {
    enumerator.dump(stream);
  }

  @Override
  protected int writeSerializerId(@NotNull ObjectStubSerializer<Stub, Stub> serializer,
                                  @NotNull IntEnumerator enumerator) throws IOException {
    return enumerator.enumerate(myEnumerator.getClassId(serializer));
  }

  @Override
  protected ObjectStubSerializer<?, Stub> getClassByIdLocal(int localId,
                                                            @Nullable Stub parentStub,
                                                            @NotNull IntEnumerator enumerator) throws SerializerNotFoundException {
    int id = enumerator.valueOf(localId);
    return myEnumerator.getClassById((id1, name, externalId) -> {
      var root = ourRootStubSerializer.get();
      return (root != null ? StubSerializationUtil.brokenStubFormat(root) : "") +
             "No serializer is registered for stub ID: " +
             id1 + ", externalId: " + externalId + ", name: " + name +
             "; parent stub class: " + (parentStub != null ? parentStub.getClass().getName() + ", parent stub type: " + parentStub.getStubType() : "null");
    }, id);
  }

  void reSerializeStub(@NotNull DataInputStream inStub,
                       @NotNull DataOutputStream outStub,
                       @NotNull StubSerializationHelper newSerializationHelper) throws IOException {
    IntEnumerator currentSerializerEnumerator = IntEnumerator.read(inStub);
    currentSerializerEnumerator.dump(outStub, id -> {
      String name = myEnumerator.getSerializerName(id);
      return name == null ? 0 : newSerializationHelper.myEnumerator.getSerializerId(name);
    });
    StreamUtil.copy(inStub, outStub);
  }
}
