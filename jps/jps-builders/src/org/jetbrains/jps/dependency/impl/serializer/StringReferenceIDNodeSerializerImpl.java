// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl.serializer;

import org.jetbrains.jps.dependency.SerializableGraphElement;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class StringReferenceIDNodeSerializerImpl extends NodeSerializerImpl<JvmNodeReferenceID>{
  public StringReferenceIDNodeSerializerImpl() {
    super(JvmNodeReferenceID.class);
  }

  @Override
  public <T extends SerializableGraphElement> void write(T elem, DataOutput out) throws IOException {
    String p = ((JvmNodeReferenceID)elem).getNodeName();
    out.writeUTF(p);
  }

  @Override
  public <T extends SerializableGraphElement> T read(DataInput in) throws IOException {
    String s = in.readUTF();

    return (T)new JvmNodeReferenceID(s);
  }
}
