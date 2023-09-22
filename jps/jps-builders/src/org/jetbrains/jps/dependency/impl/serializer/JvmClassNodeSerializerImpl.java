// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl.serializer;

import com.intellij.serialization.SerializationException;
import org.jetbrains.jps.dependency.SerializableGraphElement;
import org.jetbrains.jps.dependency.java.JvmClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class JvmClassNodeSerializerImpl extends NodeSerializerImpl<JvmClass> {
  public JvmClassNodeSerializerImpl() {
    super(JvmClass.class);
  }

  @Override
  public <T extends SerializableGraphElement> void write(T elem, DataOutput out) throws IOException {
    if (!(elem instanceof JvmClass)) {
      throw new SerializationException("Wrong serializer. Expected an object of type JvmClass, but received " + elem.getClass().getName());
    }
    JvmClass jvmClass = (JvmClass) elem;
    SerializerUtil.writeJvmClass(jvmClass, out);
  }

  @Override
  public <T extends SerializableGraphElement> T read(DataInput in) throws IOException {
    return (T)SerializerUtil.readJvmClass(in);
  }
}
