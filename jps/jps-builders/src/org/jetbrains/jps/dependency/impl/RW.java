// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.util.SmartList;
import org.jetbrains.jps.javac.Iterators;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

public final class RW {
  private RW() {
  }

  public interface Writer<T> {
    void write(T obj) throws IOException;
  }
  
  public interface Reader<T> {
    T read() throws IOException;
  }

  public static <T> void writeCollection(DataOutput out, Iterable<? extends T> seq, Writer<? super T> writable) throws IOException {
    Collection<? extends T> col = seq instanceof Collection? (Collection<? extends T>)seq : Iterators.collect(seq, new SmartList<>());
    out.writeInt(col.size());
    for (T t : col) {
      writable.write(t);
    }
  }

  public static <T> Collection<T> readCollection(DataInput in, Reader<? extends T> reader) throws IOException {
    return readCollection(in, reader, new SmartList<>());
  }

  public static <T> Collection<T> readCollection(DataInput in, Reader<? extends T> reader, Collection<T> acc) throws IOException {
    int size = in.readInt();
    while (size-- > 0) {
      acc.add(reader.read());
    }
    return acc;
  }

}
