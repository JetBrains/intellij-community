// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.index;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.*;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;

public class StubReSerializationTest extends JavaCodeInsightFixtureTestCase {
  public void testReSerializationWithDifferentSerializerEnumeration() throws Exception {
    SerializationManagerEx serializationManager = SerializationManagerEx.getInstanceEx();
    serializationManager.flushNameStorage();

    File externalStubEnumerator =
      VfsUtilCore.virtualToIoFile(myFixture.getTempDirFixture().createFile("external_stub_serializer_enumerator/serializer.names"));

    // ensure we have different serializer's enumeration
    try (PersistentStringEnumerator enumerator = new PersistentStringEnumerator(externalStubEnumerator)) {
      for (int i = 0; i < 10000; i++) {
        enumerator.enumerate("garbage value " + i);
      }
    }

    SerializationManagerImpl externalSerializationManager = new SerializationManagerImpl(externalStubEnumerator, false);
    try {
      PsiFileStub stub = createStub();

      BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
      serializationManager.serialize(stub, out);
      byte[] serialized = out.toByteArray();

      BufferExposingByteArrayOutputStream externallySerializedOut = new BufferExposingByteArrayOutputStream();
      serializationManager.reSerialize(new ByteArrayInputStream(serialized), externallySerializedOut, externalSerializationManager);
      byte[] externalStub = externallySerializedOut.toByteArray();
      assertFalse(Arrays.equals(serialized, externalStub));

      Stub stubFromExternSerializationManager = externalSerializationManager.deserialize(new ByteArrayInputStream(externalStub));
      assertEquals(DebugUtil.stubTreeToString(stub), DebugUtil.stubTreeToString(stubFromExternSerializationManager));

      BufferExposingByteArrayOutputStream initialOut = new BufferExposingByteArrayOutputStream();
      externalSerializationManager.reSerialize(new ByteArrayInputStream(externalStub), initialOut, serializationManager);
      byte[] initialSerializedStub = initialOut.toByteArray();
      assertOrderedEquals(serialized, initialSerializedStub);
    } finally {
      Disposer.dispose(externalSerializationManager);
    }
  }

  public void testIdenticalReSerialization() throws Exception {
    SerializationManagerEx serializationManager = SerializationManagerEx.getInstanceEx();

    PsiFileStub stub = createStub();

    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
    serializationManager.serialize(stub, out);
    byte[] serialized = out.toByteArray();

    BufferExposingByteArrayOutputStream reSerializedOut = new BufferExposingByteArrayOutputStream();
    serializationManager.reSerialize(new ByteArrayInputStream(serialized), reSerializedOut, serializationManager);
    byte[] reSerialized = reSerializedOut.toByteArray();

    assertOrderedEquals(serialized, reSerialized);

    BufferExposingByteArrayOutputStream reSerializedOut2 = new BufferExposingByteArrayOutputStream();
    serializationManager.reSerialize(new ByteArrayInputStream(reSerialized), reSerializedOut2, serializationManager);
    byte[] reSerialized2 = reSerializedOut.toByteArray();

    assertOrderedEquals(serialized, reSerialized2);
  }

  private PsiFileStub createStub() {
    PsiFile f = myFixture.addFileToProject("A.java", "class A { int j = 123; void m () { Runnable r = () -> {}; }}");
    return ((PsiFileWithStubSupport)f).getStubTree().getRoot();
  }
}
