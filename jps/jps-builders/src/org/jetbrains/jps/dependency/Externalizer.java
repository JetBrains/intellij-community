// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import java.io.IOException;

public interface Externalizer<T> extends DataReader<T>, DataWriter<T> {

  static <T extends ExternalizableGraphElement> Externalizer<T> forGraphElement(DataReader<? extends T> reader) {
    return new Externalizer<>() {
      @Override
      public T load(GraphDataInput in) throws IOException {
        return reader.load(in);
      }

      @Override
      public void save(GraphDataOutput out, T value) throws IOException {
        value.write(out);
      }
    };
  }

  static <T extends ExternalizableGraphElement> Externalizer<T> forAnyGraphElement() {
    return new Externalizer<>() {
      @Override
      public T load(GraphDataInput in) throws IOException {
        return in.readGraphElement();
      }

      @Override
      public void save(GraphDataOutput out, T value) throws IOException {
        out.writeGraphElement(value);
      }
    };
  }
}
