// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.GraphDataOutput;

import java.io.DataOutput;
import java.io.IOException;

public class GraphDataOutputImpl implements GraphDataOutput {

  private final DataOutput myDelegate;

  public GraphDataOutputImpl(DataOutput delegate) {
    myDelegate = delegate;
  }

  @Override
  public void write(int b) throws IOException {
    myDelegate.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    myDelegate.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    myDelegate.write(b, off, len);
  }

  @Override
  public void writeBoolean(boolean v) throws IOException {
    myDelegate.writeBoolean(v);
  }

  @Override
  public void writeByte(int v) throws IOException {
    myDelegate.writeByte(v);
  }

  @Override
  public void writeShort(int v) throws IOException {
    myDelegate.writeShort(v);
  }

  @Override
  public void writeChar(int v) throws IOException {
    myDelegate.writeChar(v);
  }

  @Override
  public void writeInt(int v) throws IOException {
    DataInputOutputUtil.writeINT(myDelegate, v);
  }

  @Override
  public void writeLong(long v) throws IOException {
    DataInputOutputUtil.writeLONG(myDelegate, v);
  }

  @Override
  public void writeFloat(float v) throws IOException {
    myDelegate.writeFloat(v);
  }

  @Override
  public void writeDouble(double v) throws IOException {
    myDelegate.writeDouble(v);
  }

  @Override
  public void writeBytes(@NotNull String s) throws IOException {
    myDelegate.writeBytes(s);
  }

  @Override
  public void writeChars(@NotNull String s) throws IOException {
    myDelegate.writeChars(s);
  }

  @Override
  public void writeUTF(@NotNull String s) throws IOException {
    IOUtil.writeUTF(myDelegate, s);
  }

  @Override
  public <T extends ExternalizableGraphElement> void writeGraphElement(@NotNull T elem) throws IOException {
    writeUTF(elem.getClass().getName());
    elem.write(this);
  }

  @Override
  public <T extends ExternalizableGraphElement> void writeGraphElementCollection(Class<? extends T> elemType, @NotNull Iterable<T> col) throws IOException {
    writeUTF(elemType.getName());
    RW.writeCollection(this, col, elem -> elem.write(this));
  }

  public static GraphDataOutput wrap(DataOutput out) {
    return new GraphDataOutputImpl(out);
  }
  
  public interface StringEnumerator {
    int toNumber(String str) throws IOException;
  }

  public static GraphDataOutput wrap(DataOutput out, @Nullable StringEnumerator enumerator) {
    if (enumerator != null) {
      return new GraphDataOutputImpl(out) {
        @Override
        public void writeUTF(@NotNull String s) throws IOException {
          writeInt(enumerator.toNumber(s));
        }
      };
    }
    return wrap(out);
  }
}
