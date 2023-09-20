// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl.serializer;

import org.jetbrains.jps.dependency.SerializableGraphElement;
import org.jetbrains.jps.dependency.impl.FileSource;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileSourceNodeSerializerImpl extends NodeSerializerImpl<FileSource>{
  public FileSourceNodeSerializerImpl() {
    super(FileSource.class);
  }

  @Override
  public <T extends SerializableGraphElement> void write(T elem, DataOutput out) throws IOException {
    Path p = ((FileSource)elem).getPath();
    out.writeUTF(p.toString());
  }

  @Override
  public <T extends SerializableGraphElement> T read(DataInput in) throws IOException {
    String s = in.readUTF();

    return (T)new FileSource(Paths.get(s));
  }
}
