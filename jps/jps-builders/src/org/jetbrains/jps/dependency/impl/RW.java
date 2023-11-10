// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.util.SmartList;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.javac.Iterators;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

// This class is a facade collecting all necessary read/write operations for values of basic types.
// Works as an abstraction layer to allow various format implementations
public final class RW {
  private RW() {
  }

  public interface Writer<T> {
    void write(T obj) throws IOException;
  }
  
  public interface Reader<T> {
    T read() throws IOException;
  }

  public static String readUTF(DataInput in) throws IOException {
    return IOUtil.readUTF(in);
  }

  public static void writeUTF(DataOutput out, String value) throws IOException {
    IOUtil.writeUTF(out, value);
  }

  public static int readINT(@NotNull DataInput in) throws IOException {
    return DataInputOutputUtil.readINT(in);
  }

  public static void writeINT(@NotNull DataOutput out, int val) throws IOException {
    DataInputOutputUtil.writeINT(out, val);
  }

  public static long readLONG(@NotNull DataInput in) throws IOException {
    return DataInputOutputUtil.readLONG(in);
  }

  public static void writeLONG(@NotNull DataOutput out, long val) throws IOException {
    DataInputOutputUtil.writeLONG(out, val);
  }

  public static float readFLOAT(@NotNull DataInput in) throws IOException {
    return in.readFloat();
  }

  public static void writeFLOAT(@NotNull DataOutput out, float val) throws IOException {
    out.writeFloat(val);
  }

  public static double readDOUBLE(@NotNull DataInput in) throws IOException {
    return in.readDouble();
  }

  public static void writeDOUBLE(@NotNull DataOutput out, double val) throws IOException {
    out.writeDouble(val);
  }

  public static <T> void writeCollection(DataOutput out, Iterable<? extends T> seq, Writer<? super T> writable) throws IOException {
    Collection<? extends T> col = seq instanceof Collection? (Collection<? extends T>)seq : Iterators.collect(seq, new SmartList<>());
    writeINT(out, col.size());
    for (T t : col) {
      writable.write(t);
    }
  }

  public static <T> Collection<T> readCollection(DataInput in, Reader<? extends T> reader) throws IOException {
    return readCollection(in, reader, new SmartList<>());
  }

  public static <T> Collection<T> readCollection(DataInput in, Reader<? extends T> reader, Collection<T> acc) throws IOException {
    int size = readINT(in);
    while (size-- > 0) {
      acc.add(reader.read());
    }
    return acc;
  }

}
