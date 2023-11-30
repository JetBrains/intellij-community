// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl.serializer;

import com.intellij.serialization.SerializationException;
import org.jetbrains.jps.dependency.SerializableGraphElement;
import org.jetbrains.jps.dependency.java.JvmModule;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class JvmModuleNodeSerializerImpl extends NodeSerializerImpl<JvmModule> {
  public JvmModuleNodeSerializerImpl() {
    super(JvmModule.class);
  }

  @Override
  public <T extends SerializableGraphElement> void write(T elem, DataOutput out) throws IOException {
    if (!(elem instanceof JvmModule)) {
      throw new SerializationException("Wrong serializer. Expected an object of type JvmModule, but received " + elem.getClass().getName());
    }
    JvmModule jvmModule = (JvmModule) elem;
    SerializerUtil.writeJvmModule(jvmModule, out);
  }

  @Override
  public <T extends SerializableGraphElement> T read(DataInput in) throws IOException {
    return (T)SerializerUtil.readJvmModule(in);
  }
}
