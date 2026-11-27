// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.SerializationManagerEx;
import com.intellij.psi.stubs.SerializationManagerImpl;
import com.intellij.psi.stubs.Stub;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.io.PersistentStringEnumerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Arrays;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class StubReSerializationTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testReSerializationWithDifferentSerializerEnumeration() throws Exception {
    SerializationManagerEx serializationManager = SerializationManagerEx.getInstanceEx();
    serializationManager.flushNameStorage();

    Path externalStubEnumerator =
      VfsUtilCore.virtualToIoFile(codeInsightFixture.get().getTempDirFixture().createFile("external_stub_serializer_enumerator/serializer.names")).toPath();

    // ensure we have different serializer's enumeration
    try (PersistentStringEnumerator enumerator = new PersistentStringEnumerator(externalStubEnumerator)) {
      for (int i = 0; i < 10000; i++) {
        enumerator.enumerate("garbage value " + i);
      }
    }

    HelperKt.executeWithScope(coroutineScope -> {
      SerializationManagerImpl externalSerializationManager = new SerializationManagerImpl(externalStubEnumerator, false, coroutineScope);
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
        assertArrayEquals(initialSerializedStub, serialized);
      }
      finally {
        Disposer.dispose(externalSerializationManager);
      }
    });
  }

  @Test
  public void testIdenticalReSerialization() throws Exception {
    SerializationManagerEx serializationManager = SerializationManagerEx.getInstanceEx();

    PsiFileStub stub = createStub();

    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
    serializationManager.serialize(stub, out);
    byte[] serialized = out.toByteArray();

    BufferExposingByteArrayOutputStream reSerializedOut = new BufferExposingByteArrayOutputStream();
    serializationManager.reSerialize(new ByteArrayInputStream(serialized), reSerializedOut, serializationManager);
    byte[] reSerialized = reSerializedOut.toByteArray();

    assertArrayEquals(reSerialized, serialized);

    BufferExposingByteArrayOutputStream reSerializedOut2 = new BufferExposingByteArrayOutputStream();
    serializationManager.reSerialize(new ByteArrayInputStream(reSerialized), reSerializedOut2, serializationManager);
    byte[] reSerialized2 = reSerializedOut.toByteArray();

    assertArrayEquals(reSerialized2, serialized);
  }

  private PsiFileStub createStub() {
    PsiFile f = codeInsightFixture.get().addFileToProject("A.java", "class A { int j = 123; void m () { Runnable r = () -> {}; }}");
    return ReadAction.computeBlocking(() -> ((PsiFileWithStubSupport)f).getStubTree().getRoot());
  }
}
