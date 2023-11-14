// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.DataReader;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.Externalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class JvmNodeElementExternalizer {
  private static final MethodType ourConstructorType = MethodType.methodType(void.class, DataInput.class);
  private static final MethodHandles.Lookup ourLookup = MethodHandles.lookup();

  private static final Externalizer<? extends ExternalizableGraphElement> ourMultitypeExternalizer = new Externalizer<>() {

    @Override
    public ExternalizableGraphElement load(DataInput in) throws IOException {
      try {
        return (ExternalizableGraphElement)ourLookup.findConstructor(Class.forName(in.readUTF()), ourConstructorType).invoke(in);
      }
      catch(IOException e) {
        throw e;
      }
      catch (Throwable e) {
        throw new IOException(e);
      }
    }

    @Override
    public void save(DataOutput out, ExternalizableGraphElement value) throws IOException {
      out.writeUTF(value.getClass().getName());
      value.write(out);
    }
  };

  public static <T extends ExternalizableGraphElement> Externalizer<T> getMultitypeExternalizer() {
    //noinspection unchecked
    return (Externalizer<T>)ourMultitypeExternalizer;
  }

  public static <T extends ExternalizableGraphElement> Externalizer<T> getMonotypeExternalizer(DataReader<? extends T> reader) {
    return new Externalizer<>() {
      @Override
      public T load(DataInput in) throws IOException {
        return reader.load(in);
      }

      @Override
      public void save(DataOutput out, T value) throws IOException {
        value.write(out);
      }
    };
  }

}
