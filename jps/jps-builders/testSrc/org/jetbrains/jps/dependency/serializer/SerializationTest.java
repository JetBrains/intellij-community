// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.serializer;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.jps.dependency.impl.FileSource;
import org.jetbrains.jps.dependency.impl.serializer.FileSourceNodeSerializerImpl;
import org.jetbrains.jps.dependency.impl.serializer.JvmClassNodeSerializerImpl;
import org.jetbrains.jps.dependency.java.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class SerializationTest extends BasePlatformTestCase {
  public void testFileSource() throws IOException {
    FileSourceNodeSerializerImpl serializer = new FileSourceNodeSerializerImpl();
    FileSource node = new FileSource(Path.of("./src/main/Foo.java"));
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutput = new DataOutputStream(byteArrayOutputStream);
    serializer.write(node, dataOutput);
    String serializedData = byteArrayOutputStream.toString(StandardCharsets.UTF_8);

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedData.getBytes(StandardCharsets.UTF_8));
    DataInputStream dataInput = new DataInputStream(byteArrayInputStream);
    FileSource serializedNode = serializer.read(dataInput);
    assertEquals(node.getPath(), serializedNode.getPath());
  }

  public void testJvmClass() throws IOException {
    JvmClassNodeSerializerImpl serializer = new JvmClassNodeSerializerImpl();
    JvmClass node = JvmClassTestUtil.createJvmClassNode();
    JvmClass serializedNode;
    byte[] serializedData;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         DataOutputStream dataOutput = new DataOutputStream(byteArrayOutputStream)) {
      serializer.write(node, dataOutput);
      serializedData = byteArrayOutputStream.toByteArray();
    }

    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedData);
         DataInputStream dataInput = new DataInputStream(byteArrayInputStream)) {
      serializedNode = serializer.read(dataInput);
    }

    JvmClassTestUtil.checkJvmClassEquals(node, serializedNode);
  }
}
