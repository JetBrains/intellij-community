// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface NodeSerializer {

  int getId();

  <T extends SerializableGraphElement> boolean isSupported(Class<T> elemClass);

  default <T extends SerializableGraphElement> boolean isSupported(T elem) {
    return elem != null && isSupported(elem.getClass());
  }

  <T extends SerializableGraphElement> void write(T elem, DataOutput out) throws IOException;

  <T extends SerializableGraphElement> T read(DataInput in) throws IOException;
}
